package org.scanamo

import java.util.UUID

import cats.NotNull
import cats.instances.list._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.traverse._
import org.scanamo.aws.models.AmazonAttribute
import org.scanamo.error._
import simulacrum.typeclass

import scala.collection.immutable.SortedMap
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
  * >>> import org.scanamo.auto._
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
  * Left(InvalidPropertiesError(NonEmptyList(PropertyReadError(age,NoPropertyOfType(N,{S: none of your business,})), PropertyReadError(farm,MissingProperty))))
  *
  * >>> invalid.leftMap(cats.Show[error.DynamoReadError].show)
  * Left('age': not of type: 'N' was '{S: none of your business,}', 'farm': missing)
  * }}}
  *
  * Optional properties are defaulted to None
  * {{{
  * >>> case class LargelyOptional(a: Option[String], b: Option[String])
  * >>> DynamoFormat[LargelyOptional].read(DynamoFormat[Map[String, String]].write(Map("b" -> "X")))
  * Right(LargelyOptional(None,Some(X)))
  * }}}
  *
  * Custom formats can often be most easily defined using [[DynamoFormat.coercedXmap]], [[DynamoFormat.xmap]] or [[DynamoFormat.iso]]
  */
abstract class DynamoFormat[T, Value: AmazonAttribute] {
  def read(av: Value): Either[DynamoReadError, T]
  def write(t: T): Value
  def default: Option[T] = None
}

abstract class DynamoFormatAPI[AttributeValue: AmazonAttribute] extends EnumDynamoFormat[AttributeValue] {
  private def attribute[T](
    decode: AmazonAttribute[AttributeValue] => AttributeValue => T,
    propertyType: String
  )(
    encode: AmazonAttribute[AttributeValue] => AttributeValue => T => AttributeValue
  ): DynamoFormat[T, AttributeValue] =
    new DynamoFormat[T, AttributeValue] {
      override def read(av: AttributeValue): Either[DynamoReadError, T] = {
        val opt = Option(decode(implicitly[AmazonAttribute[AttributeValue]])(av))
        Either.fromOption(opt, NoPropertyOfType(propertyType, av))
      }
      override def write(t: T): AttributeValue = {
        val zero = implicitly[AmazonAttribute[AttributeValue]]
        encode(zero)(zero.init)(t)
      }
    }

  /**
    * Returns a [[DynamoFormat]] for the case where `A` and `B` are isomorphic,
    * i.e. an `A` can always be converted to a `B` and vice versa.
    *
    * If there are some values of `B` that have no corresponding value in `A`,
    * use [[DynamoFormat.xmap]] or [[DynamoFormat.coercedXmap]].
    *
    * {{{
    * >>> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    *
    * >>> case class UserId(value: String)
    *
    * >>> implicit val userIdFormat =
    * ...   DynamoFormat.iso[UserId, String](UserId.apply)(_.value)
    * >>> DynamoFormat[UserId].read(new AttributeValue().withS("Eric"))
    * Right(UserId(Eric))
    * }}}
    */
  def iso[A, B](r: B => A)(w: A => B)(implicit f: DynamoFormat[B, AttributeValue]) =
    new DynamoFormat[A, AttributeValue] {
      override def read(item: AttributeValue): Either[DynamoReadError, A] = f.read(item).map(r)
      override def write(t: A): AttributeValue = f.write(w(t))
      override val default: Option[A] = f.default.map(r)
    }

  /**
    * {{{
    * >>> import org.joda.time._
    * >>> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    *
    * >>> implicit val jodaLongFormat = DynamoFormat.xmap[DateTime, Long](
    * ...   l => Right(new DateTime(l).withZone(DateTimeZone.UTC))
    * ... )(
    * ...   _.withZone(DateTimeZone.UTC).getMillis
    * ... )
    * >>> DynamoFormat[DateTime].read(new AttributeValue().withN("0"))
    * Right(1970-01-01T00:00:00.000Z)
    * }}}
    */
  def xmap[A, B](
    r: B => Either[DynamoReadError, A]
  )(w: A => B)(implicit f: DynamoFormat[B, AttributeValue]) = new DynamoFormat[A, AttributeValue] {
    override def read(item: AttributeValue): Either[DynamoReadError, A] = f.read(item).flatMap(r)
    override def write(t: A): AttributeValue = f.write(w(t))
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
    * >>> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * >>> jodaStringFormat.read(new AttributeValue().withS("Togtogdenoggleplop"))
    * Left(TypeCoercionError(java.lang.IllegalArgumentException: Invalid format: "Togtogdenoggleplop"))
    * }}}
    */
  def coercedXmap[A, B, T >: scala.Null <: scala.Throwable](
    read: B => A
  )(write: A => B)(implicit f: DynamoFormat[B, AttributeValue], T: ClassTag[T], NT: NotNull[T]) =
    xmap(coerce[B, A, T](read))(write)

  /**
    * {{{
    * prop> (s: String) =>
    *     | DynamoFormat[String].read(DynamoFormat[String].write(s)) == Right(s)
    * }}}
    */
  implicit def stringFormat =
    attribute[String](_.getString, "S")(_.setString)

  private def numFormat: DynamoFormat[String, AttributeValue] =
    attribute[String](_.getNumericString, "N")(_.setNumericString)

  private def coerceNumber[N](f: String => N): String => Either[DynamoReadError, N] =
    coerce[String, N, NumberFormatException](f)

  private def coerce[A, B, T >: scala.Null <: scala.Throwable](
    f: A => B
  )(implicit T: ClassTag[T], NT: NotNull[T]): A => Either[DynamoReadError, B] =
    a => Either.catchOnly[T](f(a)).leftMap(TypeCoercionError(_))

  /**
    * {{{
    * prop> (l: Long) =>
    *     | DynamoFormat[Long].read(DynamoFormat[Long].write(l)) == Right(l)
    * }}}
    */
  implicit def longFormat: DynamoFormat[Long, AttributeValue] =
    xmap[Long, String](coerceNumber(_.toLong))(_.toString)(numFormat)

  /**
    * {{{
    * prop> (i: Int) =>
    *     | DynamoFormat[Int].read(DynamoFormat[Int].write(i)) == Right(i)
    * }}}
    */
  implicit def intFormat =
    xmap[Int, String](coerceNumber(_.toInt))(_.toString)(numFormat)

  /**
    * {{{
    * prop> (d: Float) =>
    *     | DynamoFormat[Float].read(DynamoFormat[Float].write(d)) == Right(d)
    * }}}
    */
  implicit val floatFormat = xmap(coerceNumber(_.toFloat))(_.toString)(numFormat)

  /**
    * {{{
    * prop> (d: Double) =>
    *     | DynamoFormat[Double].read(DynamoFormat[Double].write(d)) == Right(d)
    * }}}
    */
  implicit val doubleFormat = xmap(coerceNumber(_.toDouble))(_.toString)(numFormat)
  implicit val doubleFormat: DynamoFormatV1[Double] = ???

  /**
    * {{{
    * prop> (d: BigDecimal) =>
    *     | DynamoFormat[BigDecimal].read(DynamoFormat[BigDecimal].write(d)) == Right(d)
    * }}}
    */
  implicit val bigDecimalFormat =
    xmap(coerceNumber(BigDecimal(_)))(_.toString)(numFormat)

  /**
    * {{{
    * prop> (s: Short) =>
    *     | DynamoFormat[Short].read(DynamoFormat[Short].write(s)) == Right(s)
    * }}}
    */
  implicit val shortFormat = xmap(coerceNumber(_.toShort))(_.toString)(numFormat)

  /**
    * {{{
    * prop> (b: Byte) =>
    *     | DynamoFormat[Byte].read(DynamoFormat[Byte].write(b)) == Right(b)
    * }}}
    */
  // Thrift and therefore Scanamo-Scrooge provides a byte and binary types backed by byte and byte[].
  implicit def byteFormat = xmap(coerceNumber(_.toByte))(_.toString)(numFormat)

  /**
    * {{{
    * prop> (ab:Array[Byte]) =>
    *     | DynamoFormat[Array[Byte]].read(DynamoFormat[Array[Byte]].write(ab)) == Right(ab)
    * }}}
    */
  implicit def byteArrayFormat = attribute[Array[Byte]](_.getBytesArray, "B")(_.setBytesArray)

  /**
    * {{{
    * prop> implicit val arbitraryUUID = org.scalacheck.Arbitrary(org.scalacheck.Gen.uuid)
    * prop> (uuid: java.util.UUID) =>
    *     | DynamoFormat[java.util.UUID].read(DynamoFormat[java.util.UUID].write(uuid)) ==
    *     |   Right(uuid)
    * }}}
    */
  implicit val uuidFormat = {
    implicit val d = stringFormat
    coercedXmap[UUID, String, IllegalArgumentException](UUID.fromString)(_.toString)
  }

  val javaListFormat = attribute[List[AttributeValue]](_.getList, "L")(_.setList)

  /**
    * {{{
    * prop> (l: List[String]) =>
    *     | DynamoFormatV1[List[String]].read(DynamoFormat[List[String]].write(l)) ==
    *     |   Right(l)
    * }}}
    */
  implicit def listFormat[T](
    implicit r: DynamoFormat[T, AttributeValue]
  ): DynamoFormat[List[T], AttributeValue] =
    xmap[List[T], List[AttributeValue]](_.traverse(r.read))(items => items.map(r.write))(javaListFormat)

  /**
    * {{{
    * prop> (sq: Seq[String]) =>
    *     | DynamoFormat[Seq[String]].read(DynamoFormat[Seq[String]].write(sq)) ==
    *     |   Right(sq)
    * }}}
    */
  implicit def seqFormat[T](
    implicit f: DynamoFormat[T, AttributeValue]
  ): DynamoFormat[Seq[T], AttributeValue] =
    xmap[Seq[T], List[T]](l => Right(l))(_.toList)

  /**
    * {{{
    * prop> (v: Vector[String]) =>
    *     | DynamoFormat[Vector[String]].read(DynamoFormat[Vector[String]].write(v)) ==
    *     |   Right(v)
    * }}}
    */
  implicit def vectorFormat[T](
    implicit f: DynamoFormat[T, AttributeValue]
  ): DynamoFormat[Vector[T], AttributeValue] =
    xmap[Vector[T], List[AttributeValue]](_.toVector.traverse(f.read))(
      _.toList.map(f.write)
    )(javaListFormat)

  /**
    * {{{
    * prop> (a: Array[String]) =>
    *     | DynamoFormat[Array[String]].read(DynamoFormat[Array[String]].write(a)).right.getOrElse(Array("error")).deep ==
    *     |   a.deep
    * }}}
    */
  implicit def arrayFormat[T: ClassTag](
    implicit f: DynamoFormat[T, AttributeValue]
  ): DynamoFormat[Array[T], AttributeValue] =
    xmap[Array[T], List[AttributeValue]](_.traverse(f.read).map(_.toArray))(
      _.toArray.map(f.write).toList
    )(javaListFormat)

  private def numSetFormat[T](
    r: String => Either[DynamoReadError, T]
  )(w: T => String): DynamoFormat[Set[T], AttributeValue] =
    new DynamoFormat[Set[T], AttributeValue] {

      val hellper = implicitly[AmazonAttribute[AttributeValue]]

      override def read(av: AttributeValue): Either[DynamoReadError, Set[T]] =
        for {
          ns <- Either.fromOption(
            if (hellper.isNull(av)) Some(Nil) else Option(hellper.getNS(av)),
            NoPropertyOfType("NS", av)
          )
          set <- ns.traverse(r)
        } yield set.toSet
      // Set types cannot be empty
      override def write(t: Set[T]): AttributeValue = {
        val zero = hellper.init
        t.toList match {
          case Nil => hellper.setNull(zero)
          case xs  => hellper.setNS(zero)(xs.map(w))
        }
      }
      override val default: Option[Set[T]] = Some(Set.empty)
    }

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[Int]) =>
    *     | val av = new AttributeValue().withNS(s.toList.map(_.toString): _*)
    *     | DynamoFormat[Set[Int]].write(s) == av &&
    *     |   DynamoFormat[Set[Int]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[Int]].write(Set.empty).getNULL
    * true
    * }}}
    */
  implicit val intSetFormat: DynamoFormat[Set[Int], AttributeValue] = numSetFormat(coerceNumber(_.toInt))(_.toString)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[Long]) =>
    *     | val av = new AttributeValue().withNS(s.toList.map(_.toString): _*)
    *     | DynamoFormat[Set[Long]].write(s) == av &&
    *     |   DynamoFormat[Set[Long]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[Long]].write(Set.empty).getNULL
    * true
    * }}}
    */
  implicit val longSetFormat: DynamoFormat[Set[Long], AttributeValue] = numSetFormat(coerceNumber(_.toLong))(_.toString)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[Float]) =>
    *     | val av = new AttributeValue().withNS(s.toList.map(_.toString): _*)
    *     | DynamoFormat[Set[Float]].write(s) == av &&
    *     |   DynamoFormat[Set[Float]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[Float]].write(Set.empty).getNULL
    * true
    * }}}
    */
  implicit val floatSetFormat: DynamoFormat[Set[Float], AttributeValue] =
    numSetFormat(coerceNumber(_.toFloat))(_.toString)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[Double]) =>
    *     | val av = new AttributeValue().withNS(s.toList.map(_.toString): _*)
    *     | DynamoFormat[Set[Double]].write(s) == av &&
    *     |   DynamoFormat[Set[Double]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[Double]].write(Set.empty).getNULL
    * true
    * }}}
    */
  implicit val doubleSetFormat: DynamoFormat[Set[Double], AttributeValue] =
    numSetFormat(coerceNumber(_.toDouble))(_.toString)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> import org.scalacheck._
    * prop> implicit def arbNonEmptySet[T: Arbitrary] = Arbitrary(Gen.nonEmptyContainerOf[Set, T](Arbitrary.arbitrary[T]))
    *
    * prop> (s: Set[BigDecimal]) =>
    *     | val av = new AttributeValue().withNS(s.toList.map(_.toString): _*)
    *     | DynamoFormat[Set[BigDecimal]].write(s) == av &&
    *     |   DynamoFormat[Set[BigDecimal]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[BigDecimal]].write(Set.empty).getNULL
    * true
    * }}}
    */
  implicit val BigDecimalSetFormat: DynamoFormat[Set[BigDecimal], AttributeValue] =
    numSetFormat(coerceNumber(BigDecimal(_)))(_.toString)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> import org.scalacheck._
    * prop> implicit val arbSet = Arbitrary(Gen.nonEmptyContainerOf[Set, String](Arbitrary.arbitrary[String]))
    *
    * prop> (s: Set[String]) =>
    *     | val av = new AttributeValue().withSS(s.toList: _*)
    *     | DynamoFormat[Set[String]].write(s) == av &&
    *     |   DynamoFormat[Set[String]].read(av) == Right(s)
    *
    * >>> DynamoFormat[Set[String]].write(Set.empty).getNULL
    * true
    * }}}
    */
  implicit val stringSetFormat: DynamoFormat[Set[String], AttributeValue] =
    new DynamoFormat[Set[String], AttributeValue] {
      private val helper = implicitly[AmazonAttribute[AttributeValue]]

      override def read(av: AttributeValue): Either[DynamoReadError, Set[String]] =
        for {
          ss <- Either.fromOption(
            if (helper.isNull(av)) Some(Nil) else Option(helper.getSS(av)),
            NoPropertyOfType("SS", av)
          )
        } yield ss.toSet
      // Set types cannot be empty
      override def write(t: Set[String]): AttributeValue = t.toList match {
        case Nil => helper.setNull(helper.init)
        case xs  => helper.setSS(helper.init)(xs)
      }
      override val default: Option[Set[String]] = Some(Set.empty)
    }

  private val javaMapFormat = attribute[Map[String, AttributeValue]](_.getMap, "M")(_.setMap)

  /**
    * {{{
    * prop> (m: Map[String, Int]) =>
    *     | DynamoFormat[Map[String, Int]].read(DynamoFormat[Map[String, Int]].write(m)) ==
    *     |   Right(m)
    * }}}
    */
  implicit def mapFormat[V](implicit f: DynamoFormat[V, AttributeValue]): DynamoFormat[Map[String, V], AttributeValue] =
    xmap[Map[String, V], Map[String, AttributeValue]](
      m => (SortedMap[String, AttributeValue]() ++ m).traverse(f.read)
    )(_.mapValues(f.write))(javaMapFormat)

  /**
    * {{{
    * prop> (o: Option[Long]) =>
    *     | DynamoFormat[Option[Long]].read(DynamoFormat[Option[Long]].write(o)) ==
    *     |   Right(o)
    *
    * >>> DynamoFormat[Option[Long]].read(new com.amazonaws.services.dynamodbv2.model.AttributeValue().withNULL(true))
    * Right(None)
    *
    * >>> DynamoFormat[Option[Long]].write(None).isNULL()
    * true
    * }}}
    */
  implicit def optionFormat[T](implicit f: DynamoFormat[T, AttributeValue]) =
    new DynamoFormat[Option[T], AttributeValue] {

      val attOps = implicitly[AmazonAttribute[AttributeValue]]

      def read(av: AttributeValue): Either[DynamoReadError, Option[T]] =
        Option(av)
          .filter(x => attOps.isNull(x))
          .map(f.read(_).map(Some(_)))
          .getOrElse(Right(Option.empty[T]))

      def write(t: Option[T]): AttributeValue = t.map(f.write).getOrElse(attOps.setNull(attOps.init))
      override val default = Some(None)
    }

  /**
    * This ensures that if, for instance, you specify an update with Some(5) rather
    * than making the type of `Option` explicit, it doesn't fall back to auto-derivation
    */
  implicit def someFormat[T](implicit f: DynamoFormat[T, AttributeValue]) =
    new DynamoFormat[Some[T], AttributeValue] {
      def read(av: AttributeValue): Either[DynamoReadError, Some[T]] =
        Option(av).map(f.read(_).map(Some(_))).getOrElse(Left[DynamoReadError, Some[T]](MissingProperty))

      def write(t: Some[T]): AttributeValue = f.write(t.get)
    }
}
@typeclass trait DynamoFormatV1[T] extends DynamoFormat[T, com.amazonaws.services.dynamodbv2.model.AttributeValue]
object DynamoFormatV1 extends DynamoFormatAPI[com.amazonaws.services.dynamodbv2.model.AttributeValue] {}

// TODO: dunno what to do...
object A extends App {
  val z = DynamoFormatV1[String]
}
