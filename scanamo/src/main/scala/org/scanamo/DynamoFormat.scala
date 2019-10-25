package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.nio.ByteBuffer
import java.time.{ Instant, OffsetDateTime, ZonedDateTime }
import java.time.format.{ DateTimeFormatter, DateTimeParseException }
import java.util.UUID

import cats.instances.either._
import cats.instances.list._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.traverse._

import scala.reflect.ClassTag

/**
  * Type class for defining serialisation to and from
  * DynamoDB's `AttributeValue`
  *
  * {{{
  * >>> val listOptionFormat = DynamoFormat[List[Option[Int]]]
  * >>> listOptionFormat.read(listOptionFormat.write(List(Some(1), None, Some(3))))
  * Right(List(Some(1), None, Some(3)))
  *
  * Also supports automatic and semi-automatic derivation for case classes
  *
  * }}}
  *
  * {{{
  * >>> import org.scanamo.generic.auto._
  * >>>
  * >>> case class Farm(animals: List[String])
  * >>> case class Farmer(name: String, age: Long, farm: Farm)
  * >>> val farmerF = DynamoFormat[Farmer]
  * >>> farmerF.read(farmerF.write(Farmer("McDonald", 156L, Farm(List("sheep", "cow")))))
  * Right(Farmer(McDonald,156,Farm(List(sheep, cow))))
  * }}}
  *
  * and for sealed trait + case object hierarchies
  *
  * {{{
  * >>> sealed trait Animal
  * >>> case object Aardvark extends Animal
  * >>> case object Zebra extends Animal
  * >>> case class Pet(name: String, animal: Animal)
  * >>> val petF = DynamoFormat[Pet]
  * >>> petF.read(petF.write(Pet("Amy", Aardvark)))
  * Right(Pet(Amy,Aardvark))
  *
  * >>> petF.read(petF.write(Pet("Zebediah", Zebra)))
  * Right(Pet(Zebediah,Zebra))
  * }}}
  *
  * Problems reading a value are detailed
  * {{{
  * >>> import cats.syntax.either._
  *
  * >>> case class Developer(name: String, age: String, problems: Int)
  * >>> val invalid = DynamoFormat[Farmer].read(DynamoFormat[Developer].write(Developer("Alice", "none of your business", 99)))
  * >>> invalid
  * Left(InvalidPropertiesError(NonEmptyList((age,NoPropertyOfType(N,DynString(none of your business))), (farm,MissingProperty))))
  *
  * >>> invalid.leftMap(cats.Show[DynamoReadError].show)
  * Left('age': not of type: 'N' was 'DynString(none of your business)', 'farm': missing)
  * }}}
  *
  * Custom formats can often be most easily defined using [[DynamoFormat.coercedXmap]], [[DynamoFormat.xmap]] or [[DynamoFormat.iso]]
  */
trait DynamoFormat[T] {
  def read(av: DynamoValue): Either[DynamoReadError, T]
  def read(av: AttributeValue): Either[DynamoReadError, T] = read(DynamoValue.fromAttributeValue(av))
  def write(t: T): DynamoValue
}

object DynamoFormat extends LowPriorityFormats {
  def apply[T](implicit D: DynamoFormat[T]): DynamoFormat[T] = D

  def build[T](r: DynamoValue => Either[DynamoReadError, T], w: T => DynamoValue): DynamoFormat[T] =
    new DynamoFormat[T] {
      def read(av: DynamoValue): Either[DynamoReadError, T] = r(av)
      def write(t: T): DynamoValue = w(t)
    }

  trait ObjectFormat[T] extends DynamoFormat[T] {
    def readObject(o: DynamoObject): Either[DynamoReadError, T]
    def writeObject(t: T): DynamoObject

    def read(dv: DynamoValue): Either[DynamoReadError, T] =
      dv.asObject.fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("M", dv)))(readObject)

    def write(t: T): DynamoValue =
      writeObject(t).toDynamoValue
  }

  object ObjectFormat {
    def apply[T](implicit T: ObjectFormat[T]): ObjectFormat[T] = T

    def build[T](r: DynamoObject => Either[DynamoReadError, T], w: T => DynamoObject): ObjectFormat[T] =
      new ObjectFormat[T] {
        def readObject(o: DynamoObject): Either[DynamoReadError, T] = r(o)
        def writeObject(t: T): DynamoObject = w(t)
      }
  }

  private def coerceNumber[N: Numeric](f: String => N): String => Either[DynamoReadError, N] =
    coerce[String, N, NumberFormatException](f)

  private def coerce[A, B, T >: Null <: Throwable: ClassTag](f: A => B): A => Either[DynamoReadError, B] =
    a => Either.catchOnly[T](f(a)).leftMap(TypeCoercionError(_))

  private def coerceByteBuffer[B](f: ByteBuffer => B): ByteBuffer => Either[DynamoReadError, B] =
    coerce[ByteBuffer, B, IllegalArgumentException](f)

  private def attribute[T](
    decode: DynamoValue => Option[T],
    encode: T => DynamoValue,
    propertyType: String
  ): DynamoFormat[T] =
    new DynamoFormat[T] {
      final def read(av: DynamoValue): Either[DynamoReadError, T] =
        Either.fromOption(decode(av), NoPropertyOfType(propertyType, av))
      final def write(t: T): DynamoValue = encode(t)
    }

  /**
    * Returns a [[DynamoFormat]] for the case where `A` and `B` are isomorphic,
    * i.e. an `A` can always be converted to a `B` and vice versa.
    *
    * If there are some values of `B` that have no corresponding value in `A`,
    * use [[DynamoFormat.xmap]] or [[DynamoFormat.coercedXmap]].
    *
    * {{{
    * >>> case class UserId(value: String)
    *
    * >>> implicit val userIdFormat =
    * ...   DynamoFormat.iso[UserId, String](UserId.apply, _.value)
    * >>> DynamoFormat[UserId].read(DynamoValue.fromString("Eric"))
    * Right(UserId(Eric))
    * }}}
    */
  def iso[A, B](r: B => A, w: A => B)(implicit f: DynamoFormat[B]) =
    new DynamoFormat[A] {
      final def read(item: DynamoValue) = f.read(item).map(r)
      final def write(t: A) = f.write(w(t))
    }

  /**
    * {{{
    * >>> import org.joda.time._
    *
    * >>> implicit val jodaLongFormat = DynamoFormat.xmap[DateTime, Long](
    * ...   l => Right(new DateTime(l).withZone(DateTimeZone.UTC))
    * ... )(
    * ...   _.withZone(DateTimeZone.UTC).getMillis
    * ... )
    * >>> DynamoFormat[DateTime].read(DynamoValue.fromNumber(0L))
    * Right(1970-01-01T00:00:00.000Z)
    * }}}
    */
  def xmap[A, B](r: B => Either[DynamoReadError, A])(w: A => B)(implicit f: DynamoFormat[B]) = new DynamoFormat[A] {
    final def read(item: DynamoValue) = f.read(item).flatMap(r)
    final def write(t: A) = f.write(w(t))
  }

  /**
    * Returns a [[DynamoFormat]] for the case where `A` can always be converted `B`,
    * with `write`, but `read` may throw an exception for some value of `B`
    *
    * {{{
    * >>> import org.joda.time._
    *
    * >>> val jodaStringFormat = DynamoFormat.coercedXmap[LocalDate, String, IllegalArgumentException](
    * ...   LocalDate.parse
    * ... )(
    * ...   _.toString
    * ... )
    * >>> jodaStringFormat.read(jodaStringFormat.write(new LocalDate(2007, 8, 18)))
    * Right(2007-08-18)
    *
    * >>> jodaStringFormat.read(DynamoValue.fromString("Togtogdenoggleplop"))
    * Left(TypeCoercionError(java.lang.IllegalArgumentException: Invalid format: "Togtogdenoggleplop"))
    * }}}
    */
  def coercedXmap[A, B: DynamoFormat, T >: Null <: Throwable: ClassTag](read: B => A)(write: A => B) =
    xmap(coerce[B, A, T](read))(write)

  /**
    * {{{
    * prop> (s: String) =>
    *     | DynamoFormat[String].read(DynamoFormat[String].write(s)) == Right(s)
    * }}}
    */
  implicit val stringFormat: DynamoFormat[String] = new DynamoFormat[String] {
    final def read(av: DynamoValue) =
      if (av.isNull)
        Right("")
      else
        av.asString.fold[Either[DynamoReadError, String]](Left(NoPropertyOfType("S", av)))(Right(_))

    final def write(s: String): DynamoValue = s match {
      case "" => DynamoValue.nil
      case _  => DynamoValue.fromString(s)
    }
  }

  /**
    * {{{
    * prop> (b: Boolean) =>
    *     | DynamoFormat[Boolean].read(DynamoFormat[Boolean].write(b)) == Right(b)
    * }}}
    */
  implicit val booleanFormat: DynamoFormat[Boolean] = attribute(_.asBoolean, DynamoValue.fromBoolean, "BOOL")

  private def numFormat[N: Numeric](f: String => N): DynamoFormat[N] = new DynamoFormat[N] {
    final def read(av: DynamoValue) =
      for {
        ns <- Either.fromOption(av.asNumber, NoPropertyOfType("N", av))
        transform = coerceNumber(f)
        n <- transform(ns)
      } yield n

    final def write(n: N) = DynamoValue.fromNumber(n)
  }

  /**
    * {{{
    * prop> (l: Long) =>
    *     | DynamoFormat[Long].read(DynamoFormat[Long].write(l)) == Right(l)
    * }}}
    */
  implicit val longFormat: DynamoFormat[Long] = numFormat(_.toLong)

  /**
    * {{{
    * prop> (i: Int) =>
    *     | DynamoFormat[Int].read(DynamoFormat[Int].write(i)) == Right(i)
    * }}}
    */
  implicit val intFormat: DynamoFormat[Int] = numFormat(_.toInt)

  /**
    * {{{
    * prop> (d: Float) =>
    *     | DynamoFormat[Float].read(DynamoFormat[Float].write(d)) == Right(d)
    * }}}
    */
  implicit val floatFormat: DynamoFormat[Float] = numFormat(_.toFloat)

  /**
    * {{{
    * prop> (d: Double) =>
    *     | DynamoFormat[Double].read(DynamoFormat[Double].write(d)) == Right(d)
    * }}}
    */
  implicit val doubleFormat: DynamoFormat[Double] = numFormat(_.toDouble)

  /**
    * {{{
    * prop> (d: BigDecimal) =>
    *     | DynamoFormat[BigDecimal].read(DynamoFormat[BigDecimal].write(d)) == Right(d)
    * }}}
    */
  implicit val bigDecimalFormat: DynamoFormat[BigDecimal] = numFormat(BigDecimal(_))

  /**
    * {{{
    * prop> (s: Short) =>
    *     | DynamoFormat[Short].read(DynamoFormat[Short].write(s)) == Right(s)
    * }}}
    */
  implicit val shortFormat: DynamoFormat[Short] = numFormat(_.toShort)

  /**
    * {{{
    * prop> (b: Byte) =>
    *     | DynamoFormat[Byte].read(DynamoFormat[Byte].write(b)) == Right(b)
    * }}}
    */
  // Thrift and therefore Scanamo-Scrooge provides a byte and binary types backed by byte and byte[].
  implicit val byteFormat: DynamoFormat[Byte] = numFormat(_.toByte)

  // Since DynamoValue includes a ByteBuffer instance, creating byteArray format backed by ByteBuffer
  implicit val byteBufferFormat: DynamoFormat[ByteBuffer] = attribute(_.asByteBuffer, DynamoValue.fromByteBuffer, "B")

  /**
    * {{{
    * prop> (ab:Array[Byte]) =>
    *     | DynamoFormat[Array[Byte]].read(DynamoFormat[Array[Byte]].write(ab)) == Right(ab)
    * }}}
    */
  implicit val byteArrayFormat: DynamoFormat[Array[Byte]] =
    xmap(coerceByteBuffer(_.array))(ByteBuffer.wrap(_))(byteBufferFormat)

  /**
    * {{{
    * prop> (uuid: java.util.UUID) =>
    *     | DynamoFormat[java.util.UUID].read(DynamoFormat[java.util.UUID].write(uuid)) ==
    *     |   Right(uuid)
    * }}}
    */
  implicit val uuidFormat: DynamoFormat[UUID] =
    coercedXmap[UUID, String, IllegalArgumentException](UUID.fromString)(_.toString)

  implicit val javaListFormat: DynamoFormat[List[DynamoValue]] =
    attribute({ dv =>
      if (dv.isNull) Some(List.empty)
      else dv.asArray.flatMap(_.asArray)
    }, l => DynamoValue.fromValues(l), "L")

  /**
    * {{{
    * prop> (l: List[String]) =>
    *     | DynamoFormat[List[String]].read(DynamoFormat[List[String]].write(l)) ==
    *     |   Right(l)
    * }}}
    */
  implicit def listFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[List[T]] =
    xmap[List[T], List[DynamoValue]](_.traverse(f.read))(_.map(f.write))(javaListFormat)

  /**
    * {{{
    * prop> (sq: Seq[String]) =>
    *     | DynamoFormat[Seq[String]].read(DynamoFormat[Seq[String]].write(sq)) ==
    *     |   Right(sq)
    * }}}
    */
  implicit def seqFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Seq[T]] =
    xmap[Seq[T], List[T]](l => Right(l.toSeq))(_.toList)

  /**
    * {{{
    * prop> (v: Vector[String]) =>
    *     | DynamoFormat[Vector[String]].read(DynamoFormat[Vector[String]].write(v)) ==
    *     |   Right(v)
    * }}}
    */
  implicit def vectorFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Vector[T]] =
    xmap[Vector[T], List[DynamoValue]](_.toVector.traverse(f.read))(_.map(f.write).toList)(javaListFormat)

  /**
    * {{{
    * prop> (a: Array[String]) =>
    *     | DynamoFormat[Array[String]].read(DynamoFormat[Array[String]].write(a)).right.getOrElse(Array("error")).toList ==
    *     |   a.toList
    * }}}
    */
  implicit def arrayFormat[T: ClassTag](implicit f: DynamoFormat[T]): DynamoFormat[Array[T]] =
    xmap[Array[T], List[DynamoValue]](_.traverse(f.read).map(_.toArray))(
      _.map(f.write).toList
    )(javaListFormat)

  private def numSetFormat[T: Numeric](r: String => Either[DynamoReadError, T]): DynamoFormat[Set[T]] =
    new DynamoFormat[Set[T]] {
      final def read(av: DynamoValue) =
        if (av.isNull)
          Right(Set.empty[T])
        else
          for {
            ns <- Either.fromOption(av.asArray.flatMap(_.asNumericArray), NoPropertyOfType("NS", av))
            set <- ns.traverse(r)
          } yield set.toSet

      // Set types cannot be empty
      final def write(t: Set[T]) =
        if (t.isEmpty)
          DynamoValue.nil
        else
          DynamoValue.fromNumbers(t)
    }

  /**
    * {{{
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[Int]) =>
    *     | val av = DynamoValue.fromNumbers(s)
    *     | DynamoFormat[Set[Int]].write(s) == av &&
    *     |   DynamoFormat[Set[Int]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[Int]].write(Set.empty).isNull
    * true
    * }}}
    */
  implicit val intSetFormat: DynamoFormat[Set[Int]] = numSetFormat(coerceNumber(_.toInt))

  /**
    * {{{
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[Long]) =>
    *     | val av = DynamoValue.fromNumbers(s)
    *     | DynamoFormat[Set[Long]].write(s) == av &&
    *     |   DynamoFormat[Set[Long]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[Long]].write(Set.empty).isNull
    * true
    * }}}
    */
  implicit val longSetFormat: DynamoFormat[Set[Long]] = numSetFormat(coerceNumber(_.toLong))

  /**
    * {{{
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[Float]) =>
    *     | val av = DynamoValue.fromNumbers(s)
    *     | DynamoFormat[Set[Float]].write(s) == av &&
    *     |   DynamoFormat[Set[Float]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[Float]].write(Set.empty).isNull
    * true
    * }}}
    */
  implicit val floatSetFormat: DynamoFormat[Set[Float]] = numSetFormat(coerceNumber(_.toFloat))

  /**
    * {{{
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[Double]) =>
    *     | val av = DynamoValue.fromNumbers(s)
    *     | DynamoFormat[Set[Double]].write(s) == av &&
    *     |   DynamoFormat[Set[Double]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[Double]].write(Set.empty).isNull
    * true
    * }}}
    */
  implicit val doubleSetFormat: DynamoFormat[Set[Double]] = numSetFormat(coerceNumber(_.toDouble))

  /**
    * {{{
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[BigDecimal]) =>
    *     | val av = DynamoValue.fromNumbers(s)
    *     | DynamoFormat[Set[BigDecimal]].write(s) == av &&
    *     |   DynamoFormat[Set[BigDecimal]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[BigDecimal]].write(Set.empty).isNull
    * true
    * }}}
    */
  implicit val BigDecimalSetFormat: DynamoFormat[Set[BigDecimal]] = numSetFormat(coerceNumber(BigDecimal(_)))

  /**
    * {{{
    * prop> import org.scalacheck._
    * prop> implicit val arbSet = Arbitrary(Gen.nonEmptyContainerOf[Set, String](Arbitrary.arbitrary[String]))
    *
    * prop> (s: Set[String]) =>
    *     | val av = DynamoValue.fromStrings(s)
    *     | DynamoFormat[Set[String]].write(s) == av &&
    *     |   DynamoFormat[Set[String]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[String]].write(Set.empty).isNull
    * true
    * }}}
    */
  implicit val stringSetFormat: DynamoFormat[Set[String]] =
    new DynamoFormat[Set[String]] {
      final def read(av: DynamoValue) =
        if (av.isNull)
          Right(Set.empty)
        else
          Either.fromOption(av.asArray.flatMap(_.asStringArray).map(_.toSet), NoPropertyOfType("SS", av))

      // Set types cannot be empty
      final def write(t: Set[String]) =
        if (t.isEmpty)
          DynamoValue.nil
        else
          DynamoValue.fromStrings(t)
    }

  /**
    * {{{
    * prop> (s: Set[Boolean]) =>
    *     | DynamoFormat[Set[Boolean]].read(DynamoFormat[Set[Boolean]].write(s)) ==
    *     |   Right(s)
    * }}}
    */
  implicit def genericSet[T: DynamoFormat]: DynamoFormat[Set[T]] = iso[Set[T], List[T]](_.toSet, _.toList)

  private val javaMapFormat: DynamoFormat[DynamoObject] =
    attribute(_.asObject, DynamoValue.fromDynamoObject, "M")

  /**
    * {{{
    * prop> (m: Map[String, Int]) =>
    *     | DynamoFormat[Map[String, Int]].read(DynamoFormat[Map[String, Int]].write(m)) ==
    *     |   Right(m)
    * }}}
    */
  implicit def mapFormat[V](implicit f: DynamoFormat[V]): DynamoFormat[Map[String, V]] =
    xmap[Map[String, V], DynamoObject](_.toMap[V])(m => DynamoObject(m.toSeq: _*))(javaMapFormat)

  /**
    * {{{
    * prop> (o: Option[Long]) =>
    *     | DynamoFormat[Option[Long]].read(DynamoFormat[Option[Long]].write(o)) ==
    *     |   Right(o)
    *
    * >>> DynamoFormat[Option[Long]].read(DynamoValue.nil)
    * Right(None)
    *
    * >>> DynamoFormat[Option[Long]].write(None).isNull
    * true
    * }}}
    */
  implicit def optionFormat[T](implicit f: DynamoFormat[T]) = new DynamoFormat[Option[T]] {
    final def read(av: DynamoValue) =
      if (av.isNull)
        Right(None)
      else
        f.read(av).map(Some(_))

    final def write(t: Option[T]): DynamoValue = t.fold(DynamoValue.nil)(f.write)
  }

  /**
    * This ensures that if, for instance, you specify an update with Some(5) rather
    * than making the type of `Option` explicit, it doesn't fall back to auto-derivation
    */
  implicit def someFormat[T](implicit f: DynamoFormat[T]) = new DynamoFormat[Some[T]] {
    def read(av: DynamoValue): Either[DynamoReadError, Some[T]] =
      Option(av).map(f.read(_).map(Some(_))).getOrElse(Left[DynamoReadError, Some[T]](MissingProperty))

    def write(t: Some[T]): DynamoValue = f.write(t.get)
  }

  /**  Format for dealing with points in time stored as the number of milliseconds since Epoch.
    *  {{{
    *  prop> import org.scanamo.DynamoFormat
    *  prop> import java.time.Instant
    *  prop> import org.scanamo.TimeGenerators.instantAsLongArb
    *  prop> (x: Instant) =>
    *      | DynamoFormat[Instant].read(DynamoFormat[Instant].write(x)) == Right(x)
    *  }}}
    */
  implicit val instantAsLongFormat =
    DynamoFormat.coercedXmap[Instant, Long, ArithmeticException](x => Instant.ofEpochMilli(x))(
      x => x.toEpochMilli
    )

  /**  Format for dealing with date-times with an offset from UTC.
    *  {{{
    *  prop> import org.scanamo.DynamoFormat
    *  prop> import java.time.OffsetDateTime
    *  prop> import org.scanamo.TimeGenerators.offsetDateTimeArb
    *  prop> (x: OffsetDateTime) =>
    *      | DynamoFormat[OffsetDateTime].read(DynamoFormat[OffsetDateTime].write(x)) == Right(x)
    *  }}}
    */
  implicit val offsetDateTimeFormat = DynamoFormat.coercedXmap[OffsetDateTime, String, DateTimeParseException](
    OffsetDateTime.parse
  )(
    _.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  )

  /**  Format for dealing with date-times with a time zone in the ISO-8601 calendar system.
    *  {{{
    *  prop> import org.scanamo.DynamoFormat
    *  prop> import java.time.ZonedDateTime
    *  prop> import org.scanamo.TimeGenerators.zonedDateTimeArb
    *  prop> (x: ZonedDateTime) =>
    *      | DynamoFormat[ZonedDateTime].read(DynamoFormat[ZonedDateTime].write(x)) == Right(x)
    *  }}}
    */
  implicit val zonedDateTimeFormat = DynamoFormat.coercedXmap[ZonedDateTime, String, DateTimeParseException](
    ZonedDateTime.parse
  )(
    _.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
  )

}

private[scanamo] trait LowPriorityFormats {

  implicit final def exportedFormat[A](implicit E: ExportedDynamoFormat[A]): DynamoFormat[A] = E.instance

}
