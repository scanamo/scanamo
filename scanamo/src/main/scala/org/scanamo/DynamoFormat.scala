/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo

import java.nio.ByteBuffer
import java.time.format.DateTimeFormatter
import java.time.{ Instant, OffsetDateTime, ZonedDateTime }
import java.util.UUID

import cats.instances.either._
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.show._
import cats.syntax.traverse._
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import scala.util.Try
import scala.reflect.ClassTag
import scala.util.control.NonFatal

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
  * >>> val pet1 = Pet("Amy", Aardvark)
  * >>> val pet2 = Pet("Zebediah", Zebra)
  * >>> val petF = DynamoFormat[Pet]
  * >>> petF.read(petF.write(pet1))
  * Right(Pet(Amy,Aardvark))
  *
  * >>> petF.read(petF.write(pet2))
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
trait DynamoFormat[T] extends Serializable {

  /** Attempts to decode a DynamoDB value as a value of type T */
  def read(av: DynamoValue): DynamoFormat.Result[T]

  /** Attempts to decode a raw attribute value as a value of type T */
  def read(av: AttributeValue): DynamoFormat.Result[T] = read(DynamoValue.fromAttributeValue(av))

  /** Encodes a value of type T as a DynamoDB value */
  def write(t: T): DynamoValue

  /** Creates an isomorphic format */
  def iso[U](r: T => U, w: U => T): DynamoFormat[U] = DynamoFormat.iso(r, w)(this)

  /** Creates a format for narrower type U */
  def xmap[U](r: T => Either[String, U], w: U => T): DynamoFormat[U] = DynamoFormat.xmap(r, w)(this)

  /** Creates a format for a coerced type U */
  def tmap[U](r: T => Try[U], w: U => T): DynamoFormat[U] = DynamoFormat.tmap(r, w)(this)
}

object DynamoFormat extends PlatformSpecificFormat {

  type Result[A] = Either[DynamoReadError, A]

  def apply[T](implicit D: DynamoFormat[T]): DynamoFormat[T] = D

  def build[T](r: DynamoValue => Result[T], w: T => DynamoValue): DynamoFormat[T] =
    new DynamoFormat[T] {
      def read(av: DynamoValue): Result[T] = r(av)
      def write(t: T): DynamoValue = w(t)
    }

  /**
    * DynamoFormats for object-like structures
    *
    * @note All data types used as the carrier type in [[Table]] operations
    * should derive an instance from this class
    */
  trait ObjectFormat[T] extends DynamoFormat[T] {
    def readObject(o: DynamoObject): Result[T]
    def writeObject(t: T): DynamoObject

    final def read(dv: DynamoValue): Result[T] =
      dv.asObject.fold[Result[T]](Left(NoPropertyOfType("M", dv)))(readObject)

    final def write(t: T): DynamoValue =
      writeObject(t).toDynamoValue
  }

  object ObjectFormat {
    def apply[T](implicit T: ObjectFormat[T]): ObjectFormat[T] = T

    def build[T](r: DynamoObject => Result[T], w: T => DynamoObject): ObjectFormat[T] =
      new ObjectFormat[T] {
        def readObject(o: DynamoObject): Result[T] = r(o)
        def writeObject(t: T): DynamoObject = w(t)
      }
  }

  trait ArrayFormat[T] extends DynamoFormat[T] {
    def readArray(a: DynamoArray): Result[T]
    def writeArray(t: T): DynamoArray

    final def read(dv: DynamoValue): Result[T] =
      dv.asArray.fold[Result[T]](Left(NoPropertyOfType("L", dv)))(readArray)

    final def write(t: T): DynamoValue =
      writeArray(t).toDynamoValue
  }

  /**
    * Returns a [[DynamoFormat]] for type `A` that is isomorphic to type `B`,
    * whenever a format for type `B` exists.
    *
    * If there are some values of `B` that have no corresponding value in `A`,
    * use [[DynamoFormat.xmap]] or [[DynamoFormat.tmap]].
    *
    * {{{
    * >>> case class UserId(value: String)
    *
    * >>> implicit val userIdFormat: DynamoFormat[UserId] =
    * ...   DynamoFormat.iso[UserId, String](UserId.apply, _.value)
    * >>> DynamoFormat[UserId].read(DynamoValue.fromString("Eric"))
    * Right(UserId(Eric))
    * }}}
    */
  def iso[A, B](r: B => A, w: A => B)(implicit f: DynamoFormat[B]): DynamoFormat[A] =
    new DynamoFormat[A] {
      final def read(item: DynamoValue): Result[A] = f.read(item).map(r)
      final def write(t: A): DynamoValue = f.write(w(t))
    }

  /**
    *  Returns a [[DynamoFormat]] for type `A` that is narrower than type `B`,
    * whenever a format for type `B` exists.
    *
    * Taking the example above, and assuming a user ID starts with an octothorpe:
    *
    * {{{
    * >>> class UserId private (val value: String) extends AnyVal
    *
    * >>> implicit val userIdFormat: DynamoFormat[UserId] = DynamoFormat.xmap[UserId, String](
    * ...   x => if (x.startsWith("#")) Right(new UserId(x)) else Left(),
    * ...   _.value
    * ... )
    * >>> DynamoFormat[DateTime].read(DynamoValue.fromNumber(0L))
    * Right(1970-01-01T00:00:00.000Z)
    * }}}
    */
  def xmap[A, B](r: B => Either[String, A], w: A => B)(implicit f: DynamoFormat[B]): DynamoFormat[A] =
    new DynamoFormat[A] {
      final def read(item: DynamoValue): Result[A] = f.read(item).flatMap(r(_).leftMap(ReadFailure(_)))
      final def write(t: A): DynamoValue = f.write(w(t))
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
    * ... ,
    * ...   _.toString
    * ... )
    * >>> jodaStringFormat.read(jodaStringFormat.write(new LocalDate(2007, 8, 18)))
    * Right(2007-08-18)
    *
    * >>> jodaStringFormat.read(DynamoValue.fromString("Togtogdenoggleplop"))
    * Left(TypeCoercionError(java.lang.IllegalArgumentException: Invalid format: "Togtogdenoggleplop"))
    * }}}
    */
  def tmap[A, B](r: B => Try[A], w: A => B)(implicit f: DynamoFormat[B]): DynamoFormat[A] =
    new DynamoFormat[A] {
      def read(x: DynamoValue): Result[A] = f.read(x).flatMap(r(_).toEither.leftMap(TypeCoercionError(_)))
      def write(t: A): DynamoValue = f.write(w(t))
    }

  private def attribute[T](
    decode: DynamoValue => Option[T],
    encode: T => DynamoValue,
    propertyType: String
  ): DynamoFormat[T] =
    new DynamoFormat[T] {
      final def read(av: DynamoValue): Result[T] =
        Either.fromOption(decode(av), NoPropertyOfType(propertyType, av))
      final def write(t: T): DynamoValue = encode(t)
    }

  implicit val dynamoValue: DynamoFormat[DynamoValue] =
    new DynamoFormat[DynamoValue] {
      def read(av: DynamoValue): Result[DynamoValue] = Right(av)

      def write(t: DynamoValue): DynamoValue = t
    }

  implicit val dynamoObject: ObjectFormat[DynamoObject] =
    new ObjectFormat[DynamoObject] {
      def readObject(o: DynamoObject): Result[DynamoObject] = Right(o)

      def writeObject(t: DynamoObject): DynamoObject = t
    }

  /**
    * {{{
    * prop> (s: String) =>
    *     | DynamoFormat[String].read(DynamoFormat[String].write(s)) == Right(s)
    * }}}
    */
  implicit val stringFormat: DynamoFormat[String] = new DynamoFormat[String] {
    final def read(av: DynamoValue): Result[String] =
      if (av.isNull)
        Right("")
      else
        av.asString.fold[Result[String]](Left(NoPropertyOfType("S", av)))(Right(_))

    final def write(s: String): DynamoValue =
      s match {
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

  abstract private class NumericFormat[N: Numeric] extends DynamoFormat[N] {
    def unsafeToNumeric(x: String): N

    final def read(av: DynamoValue): Result[N] =
      av.asNumber match {
        case None => Left(NoPropertyOfType("N", av))
        case Some(ns) =>
          try Right(unsafeToNumeric(ns))
          catch {
            case NonFatal(t) => Left(TypeCoercionError(t))
          }
      }

    final def write(n: N): DynamoValue = DynamoValue.fromNumber(n)
  }

  /**
    * {{{
    * prop> (l: Long) =>
    *     | DynamoFormat[Long].read(DynamoFormat[Long].write(l)) == Right(l)
    * }}}
    */
  implicit val longFormat: DynamoFormat[Long] =
    new NumericFormat[Long] {
      def unsafeToNumeric(x: String): Long = x.toLong
    }

  /**
    * {{{
    * prop> (i: Int) =>
    *     | DynamoFormat[Int].read(DynamoFormat[Int].write(i)) == Right(i)
    * }}}
    */
  implicit val intFormat: DynamoFormat[Int] =
    new NumericFormat[Int] {
      def unsafeToNumeric(x: String): Int = x.toInt
    }

  /**
    * {{{
    * prop> (d: Float) =>
    *     | DynamoFormat[Float].read(DynamoFormat[Float].write(d)) == Right(d)
    * }}}
    */
  implicit val floatFormat: DynamoFormat[Float] =
    new NumericFormat[Float] {
      def unsafeToNumeric(x: String): Float = x.toFloat
    }

  /**
    * {{{
    * prop> (d: Double) =>
    *     | DynamoFormat[Double].read(DynamoFormat[Double].write(d)) == Right(d)
    * }}}
    */
  implicit val doubleFormat: DynamoFormat[Double] =
    new NumericFormat[Double] {
      def unsafeToNumeric(x: String): Double = x.toDouble
    }

  /**
    * {{{
    * prop> (d: BigDecimal) =>
    *     | DynamoFormat[BigDecimal].read(DynamoFormat[BigDecimal].write(d)) == Right(d)
    * }}}
    */
  implicit val bigDecimalFormat: DynamoFormat[BigDecimal] =
    new NumericFormat[BigDecimal] {
      def unsafeToNumeric(x: String): BigDecimal = BigDecimal(x)
    }

  /**
    * {{{
    * prop> (s: Short) =>
    *     | DynamoFormat[Short].read(DynamoFormat[Short].write(s)) == Right(s)
    * }}}
    */
  implicit val shortFormat: DynamoFormat[Short] =
    new NumericFormat[Short] {
      def unsafeToNumeric(x: String): Short = x.toShort
    }

  /**
    * {{{
    * prop> (b: Byte) =>
    *     | DynamoFormat[Byte].read(DynamoFormat[Byte].write(b)) == Right(b)
    * }}}
    */
  // Thrift and therefore Scanamo-Scrooge provides a byte and binary types backed by byte and byte[].
  implicit val byteFormat: DynamoFormat[Byte] =
    new NumericFormat[Byte] {
      def unsafeToNumeric(x: String): Byte = x.toByte
    }

  // Since DynamoValue includes a ByteBuffer instance, creating byteArray format backed by ByteBuffer
  implicit val byteBufferFormat: DynamoFormat[ByteBuffer] = attribute(_.asByteBuffer, DynamoValue.fromByteBuffer, "B")

  /**
    * {{{
    * prop> (ab:Array[Byte]) =>
    *     | DynamoFormat[Array[Byte]].read(DynamoFormat[Array[Byte]].write(ab)) == Right(ab)
    * }}}
    */
  implicit val byteArrayFormat: DynamoFormat[Array[Byte]] =
    DynamoFormat.tmap[Array[Byte], ByteBuffer](x => Try(x.array), ByteBuffer.wrap)(byteBufferFormat)

  /**
    * {{{
    * prop> (a: Array[String]) =>
    *     | DynamoFormat[Array[String]].read(DynamoFormat[Array[String]].write(a)).right.getOrElse(Array("error")).toList ==
    *     |   a.toList
    * }}}
    */
  implicit def arrayFormat[T: DynamoFormat: ClassTag]: DynamoFormat[Array[T]] =
    listFormat[T].iso(_.toArray, _.toList)

  /**
    * {{{
    * prop> (uuid: java.util.UUID) =>
    *     | DynamoFormat[java.util.UUID].read(DynamoFormat[java.util.UUID].write(uuid)) ==
    *     |   Right(uuid)
    * }}}
    */
  implicit val uuidFormat: DynamoFormat[UUID] =
    DynamoFormat.tmap[UUID, String](s => Try(UUID.fromString(s)), _.toString)

  /**
    * {{{
    * prop> (l: List[String]) =>
    *     | DynamoFormat[List[String]].read(DynamoFormat[List[String]].write(l)) ==
    *     |   Right(l)
    * }}}
    */
  implicit def listFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[List[T]] =
    new ArrayFormat[List[T]] {
      def readArray(a: DynamoArray): Result[List[T]] = a.asArray.traverse(f.read)

      def writeArray(t: List[T]): DynamoArray = DynamoArray(t.map(f.write))

    }

  /**
    * {{{
    * prop> (sq: Seq[String]) =>
    *     | DynamoFormat[Seq[String]].read(DynamoFormat[Seq[String]].write(sq)) ==
    *     |   Right(sq)
    * }}}
    */
  implicit def seqFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Seq[T]] =
    DynamoFormat.iso[Seq[T], List[T]](_.toSeq, _.toList)

  /**
    * {{{
    * prop> (v: Vector[String]) =>
    *     | DynamoFormat[Vector[String]].read(DynamoFormat[Vector[String]].write(v)) ==
    *     |   Right(v)
    * }}}
    */
  implicit def vectorFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Vector[T]] =
    DynamoFormat.iso[Vector[T], List[T]](_.toVector, _.toList)

  abstract private class NumericSetFormat[T: Numeric] extends DynamoFormat[Set[T]] {
    def unsafeFromString(x: String): T

    final def read(av: DynamoValue): Result[Set[T]] =
      if (av.isNull)
        Right(Set.empty[T])
      else
        for {
          ns <- Either.fromOption(av.asArray.flatMap(_.asNumericArray), NoPropertyOfType("NS", av))
          set <- ns.traverse { n =>
            try Either.right[DynamoReadError, T](unsafeFromString(n))
            catch {
              case NonFatal(x) => Left(TypeCoercionError(x))
            }
          }
        } yield set.toSet

    // Set types cannot be empty
    final def write(t: Set[T]): DynamoValue =
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
  implicit val intSetFormat: DynamoFormat[Set[Int]] =
    new NumericSetFormat[Int] {
      def unsafeFromString(x: String): Int = x.toInt
    }

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
  implicit val longSetFormat: DynamoFormat[Set[Long]] =
    new NumericSetFormat[Long] {
      def unsafeFromString(x: String): Long = x.toLong
    }

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
  implicit val floatSetFormat: DynamoFormat[Set[Float]] =
    new NumericSetFormat[Float] {
      def unsafeFromString(x: String): Float = x.toFloat
    }

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
  implicit val doubleSetFormat: DynamoFormat[Set[Double]] =
    new NumericSetFormat[Double] {
      def unsafeFromString(x: String): Double = x.toDouble
    }

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
  implicit val BigDecimalSetFormat: DynamoFormat[Set[BigDecimal]] =
    new NumericSetFormat[BigDecimal] {
      def unsafeFromString(x: String): BigDecimal = BigDecimal(x)
    }

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
      final def read(av: DynamoValue): Result[Set[String]] =
        if (av.isNull)
          Right(Set.empty)
        else
          Either.fromOption(av.asArray.flatMap(_.asStringArray).map(_.toSet), NoPropertyOfType("SS", av))

      // Set types cannot be empty
      final def write(t: Set[String]): DynamoValue =
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
  implicit def genericSet[T: DynamoFormat]: DynamoFormat[Set[T]] =
    DynamoFormat.iso[Set[T], List[T]](_.toSet, _.toList)

  /**
    * {{{
    * prop> (m: Map[String, Int]) =>
    *     | DynamoFormat[Map[String, Int]].read(DynamoFormat[Map[String, Int]].write(m)) ==
    *     |   Right(m)
    * }}}
    */
  implicit def mapFormat[V: DynamoFormat]: DynamoFormat[Map[String, V]] =
    xmap[Map[String, V], DynamoObject](_.toMap[V].leftMap(_.show), m => DynamoObject(m.toSeq: _*))

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
  implicit def optionFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Option[T]] =
    new DynamoFormat[Option[T]] {
      final def read(av: DynamoValue): Result[Option[T]] =
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
  implicit def someFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Some[T]] =
    f.iso(Some.apply, _.value)

  /**  Format for dealing with points in time stored as the number of milliseconds since Epoch.
    *  {{{
    *  prop> import org.scanamo.DynamoFormat
    *  prop> import java.time.Instant
    *  prop> import org.scanamo.TimeGenerators.instantAsLongArb
    *  prop> (x: Instant) =>
    *      | DynamoFormat[Instant].read(DynamoFormat[Instant].write(x)) == Right(x)
    *  }}}
    */
  implicit val instantAsLongFormat: DynamoFormat[Instant] =
    DynamoFormat.tmap[Instant, Long](x => Try(Instant.ofEpochMilli(x)), _.toEpochMilli)

  /**  Format for dealing with date-times with an offset from UTC.
    *  {{{
    *  prop> import org.scanamo.DynamoFormat
    *  prop> import java.time.OffsetDateTime
    *  prop> import org.scanamo.TimeGenerators.offsetDateTimeArb
    *  prop> (x: OffsetDateTime) =>
    *      | DynamoFormat[OffsetDateTime].read(DynamoFormat[OffsetDateTime].write(x)) == Right(x)
    *  }}}
    */
  implicit val offsetDateTimeFormat: DynamoFormat[OffsetDateTime] =
    DynamoFormat.tmap[OffsetDateTime, String](
      s => Try(OffsetDateTime.parse(s)),
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
  implicit val zonedDateTimeFormat: DynamoFormat[ZonedDateTime] =
    DynamoFormat.tmap[ZonedDateTime, String](
      s => Try(ZonedDateTime.parse(s)),
      _.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    )
}
