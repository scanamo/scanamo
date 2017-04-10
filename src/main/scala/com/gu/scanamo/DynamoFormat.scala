package com.gu.scanamo

import java.nio.ByteBuffer
import java.util.UUID

import cats.NotNull
import cats.instances.either._
import cats.instances.list._
import cats.instances.map._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.traverse._
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.error._
import simulacrum.typeclass

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

/**
  * Type class for defining serialisation to and from
  * DynamoDB's `AttributeValue`
  *
  * {{{
  * >>> val listOptionFormat = DynamoFormat[List[Option[Int]]]
  * >>> listOptionFormat.read(listOptionFormat.write(List(Some(1), None, Some(3))))
  * Right(List(Some(1), None, Some(3)))
  * }}}
  *
  * Also supports automatic derivation for case classes
  *
  * {{{
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
  * as well as more complex sealed trait + case class hierarchies
  *
  * {{{
  * >>> sealed trait Monster
  * >>> case object Godzilla extends Monster
  * >>> case class GiantSquid(tentacles: Int) extends Monster
  * >>> val monsterF = DynamoFormat[Monster]
  * >>> monsterF.read(monsterF.write(Godzilla))
  * Right(Godzilla)
  *
  * >>> monsterF.read(monsterF.write(GiantSquid(12)))
  * Right(GiantSquid(12))
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
@typeclass trait DynamoFormat[T] {
  def read(av: AttributeValue): Either[DynamoReadError, T]
  def write(t: T): AttributeValue
  def default: Option[T] = None
}

object DynamoFormat extends EnumDynamoFormat {
  private def attribute[T](
    decode: AttributeValue => T, propertyType: String)(
    encode: AttributeValue => T => AttributeValue
  ): DynamoFormat[T] = {
    new DynamoFormat[T] {
      override def read(av: AttributeValue): Either[DynamoReadError, T] =
        Either.fromOption(Option(decode(av)), NoPropertyOfType(propertyType, av))
      override def write(t: T): AttributeValue =
        encode(new AttributeValue())(t)
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
  def iso[A, B](r: B => A)(w: A => B)(implicit f: DynamoFormat[B]) = new DynamoFormat[A] {
    override def read(item: AttributeValue): Either[DynamoReadError, A] = f.read(item).map(r)
    override def write(t: A): AttributeValue = f.write(w(t))
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
  def xmap[A, B](r: B => Either[DynamoReadError, A])(w: A => B)(implicit f: DynamoFormat[B]) = new DynamoFormat[A] {
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
  def coercedXmap[A, B, T >: scala.Null <: scala.Throwable](read: B => A)(write: A => B)(implicit f: DynamoFormat[B], T: ClassTag[T], NT: NotNull[T]) =
    xmap(coerce[B, A, T](read))(write)

  /**
    * {{{
    * prop> (s: String) =>
    *     | DynamoFormat[String].read(DynamoFormat[String].write(s)) == Right(s)
    * }}}
    */
  implicit val stringFormat = attribute(_.getS, "S")(_.withS)

  private val javaBooleanFormat = attribute[java.lang.Boolean](_.getBOOL, "BOOL")(_.withBOOL)

  /**
    * {{{
    * prop> (b: Boolean) =>
    *     | DynamoFormat[Boolean].read(DynamoFormat[Boolean].write(b)) == Right(b)
    * }}}
    */
  implicit val booleanFormat = xmap[Boolean, java.lang.Boolean](
    b => Right(Boolean.unbox(b)))(
    Boolean.box
  )(javaBooleanFormat)

  private val numFormat = attribute(_.getN, "N")(_.withN)
  private def coerceNumber[N](f: String => N): String => Either[DynamoReadError, N] =
    coerce[String, N, NumberFormatException](f)

  private def coerce[A, B, T >: scala.Null <: scala.Throwable](f: A => B)(implicit T: ClassTag[T], NT: NotNull[T]): A => Either[DynamoReadError, B] = a =>
    Either.catchOnly[T](f(a)).leftMap(TypeCoercionError(_))

  /**
    * {{{
    * prop> (l: Long) =>
    *     | DynamoFormat[Long].read(DynamoFormat[Long].write(l)) == Right(l)
    * }}}
    */
  implicit val longFormat = xmap(coerceNumber(_.toLong))(_.toString)(numFormat)

  /**
    * {{{
    * prop> (i: Int) =>
    *     | DynamoFormat[Int].read(DynamoFormat[Int].write(i)) == Right(i)
    * }}}
    */
  implicit val intFormat = xmap(coerceNumber(_.toInt))(_.toString)(numFormat)

  /**
    * {{{
    * prop> (d: Double) =>
    *     | DynamoFormat[Double].read(DynamoFormat[Double].write(d)) == Right(d)
    * }}}
    */
  implicit val doubleFormat = xmap(coerceNumber(_.toDouble))(_.toString)(numFormat)

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
  implicit val byteFormat = xmap(coerceNumber(_.toByte))(_.toString)(numFormat)

  // Since AttributeValue includes a ByteBuffer instance, creating byteArray format backed by ByteBuffer
  private val javaByteBufferFormat = attribute[java.nio.ByteBuffer](_.getB, "B")(_.withB)

  private def coerceByteBuffer[B](f: ByteBuffer => B): ByteBuffer => Either[DynamoReadError, B] =
    coerce[ByteBuffer,B, IllegalArgumentException](f)

  /**
    * {{{
    * prop> (ab:Array[Byte]) =>
    *     | DynamoFormat[Array[Byte]].read(DynamoFormat[Array[Byte]].write(ab)) == Right(ab)
    * }}}
    */
  implicit val byteArrayFormat = xmap(coerceByteBuffer(_.array()))(a => ByteBuffer.wrap(a))(javaByteBufferFormat)

  /**
    * {{{
    * prop> implicit val arbitraryUUID = org.scalacheck.Arbitrary(org.scalacheck.Gen.uuid)
    * prop> (uuid: java.util.UUID) =>
    *     | DynamoFormat[java.util.UUID].read(DynamoFormat[java.util.UUID].write(uuid)) ==
    *     |   Right(uuid)
    * }}}
    */
  implicit val uuidFormat = coercedXmap[UUID, String, IllegalArgumentException](UUID.fromString)(_.toString)

  val javaListFormat = attribute(_.getL, "L")(_.withL)

  /**
    * {{{
    * prop> (l: List[String]) =>
    *     | DynamoFormat[List[String]].read(DynamoFormat[List[String]].write(l)) ==
    *     |   Right(l)
    * }}}
    */
  implicit def listFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[List[T]] =
    xmap[List[T], java.util.List[AttributeValue]](
      _.asScala.toList.traverseU(f.read))(
      _.map(f.write).asJava
    )(javaListFormat)

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
    xmap[Vector[T], java.util.List[AttributeValue]](
      _.asScala.toVector.traverseU(f.read))(
      _.map(f.write).asJava
    )(javaListFormat)

  /**
    * {{{
    * prop> (a: Array[String]) =>
    *     | DynamoFormat[Array[String]].read(DynamoFormat[Array[String]].write(a)).right.getOrElse(Array("error")).deep ==
    *     |   a.deep
    * }}}
    */
  implicit def arrayFormat[T:ClassTag](implicit f: DynamoFormat[T]): DynamoFormat[Array[T]] =
    xmap[Array[T], java.util.List[AttributeValue]](
      _.asScala.toList.traverseU(f.read).map(_.toArray))(
      _.map(f.write).toList.asJava
    )(javaListFormat)

  private val javaNumSetFormat = attribute(_.getNS, "NS")(_.withNS)
  private val javaStringSetFormat = attribute(_.getSS, "SS")(_.withSS)
  private def setFormat[T](r: String => Either[DynamoReadError, T])(w: T => String)(df: DynamoFormat[java.util.List[String]]): DynamoFormat[Set[T]] =
    xmap[Set[T], java.util.List[String]](
      _.asScala.toList.traverseU(r).map(_.toSet))(
      _.map(w).toList.asJava
    )(df)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> (s: Set[Int]) =>
    *     | val av = new AttributeValue().withNS(s.map(_.toString).toList: _*)
    *     | DynamoFormat[Set[Int]].write(s) == av &&
    *     |   DynamoFormat[Set[Int]].read(av) == Right(s)
    * }}}
    */
  implicit val intSetFormat = setFormat(coerceNumber(_.toInt))(_.toString)(javaNumSetFormat)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> (s: Set[Long]) =>
    *     | val av = new AttributeValue().withNS(s.map(_.toString).toList: _*)
    *     | DynamoFormat[Set[Long]].write(s) == av &&
    *     |   DynamoFormat[Set[Long]].read(av) == Right(s)
    * }}}
    */
  implicit val longSetFormat = setFormat(coerceNumber(_.toLong))(_.toString)(javaNumSetFormat)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> (s: Set[Double]) =>
    *     | val av = new AttributeValue().withNS(s.map(_.toString).toList: _*)
    *     | DynamoFormat[Set[Double]].write(s) == av &&
    *     |   DynamoFormat[Set[Double]].read(av) == Right(s)
    * }}}
    */
  implicit val doubleSetFormat = setFormat(coerceNumber(_.toDouble))(_.toString)(javaNumSetFormat)

  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> (s: Set[String]) =>
    *     | val av = new AttributeValue().withSS(s.toList: _*)
    *     | DynamoFormat[Set[String]].write(s) == av &&
    *     |   DynamoFormat[Set[String]].read(av) == Right(s)
    * }}}
    */
  implicit val stringSetFormat =
    xmap[Set[String], java.util.List[String]](
      s => Right(s.asScala.toSet))(
      _.toList.asJava
    )(javaStringSetFormat)

  private val javaMapFormat = attribute(_.getM, "M")(_.withM)

  /**
    * {{{
    * prop> (m: Map[String, Int]) =>
    *     | DynamoFormat[Map[String, Int]].read(DynamoFormat[Map[String, Int]].write(m)) ==
    *     |   Right(m)
    * }}}
    */
  implicit def mapFormat[V](implicit f: DynamoFormat[V]): DynamoFormat[Map[String, V]] =
    xmap[Map[String, V], java.util.Map[String, AttributeValue]](
      _.asScala.toMap.traverseU(f.read))(
      _.mapValues(f.write).asJava
    )(javaMapFormat)

  /**
    * {{{
    * prop> (o: Option[Long]) =>
    *     | DynamoFormat[Option[Long]].read(DynamoFormat[Option[Long]].write(o)) ==
    *     |   Right(o)
    *
    * >>> DynamoFormat[Option[Long]].read(new com.amazonaws.services.dynamodbv2.model.AttributeValue().withNULL(true))
    * Right(None)
    * }}}
    */
  implicit def optionFormat[T](implicit f: DynamoFormat[T]) = new DynamoFormat[Option[T]] {
    def read(av: AttributeValue): Either[DynamoReadError, Option[T]] = {
      Option(av).filter(x => !Boolean.unbox(x.isNULL)).map(f.read(_).map(Some(_)))
        .getOrElse(Right(Option.empty[T]))
    }

    def write(t: Option[T]): AttributeValue = t.map(f.write).getOrElse(null)
    override val default = Some(None)
  }

  /**
    * This ensures that if, for instance, you specify an update with Some(5) rather
    * than making the type of `Option` explicit, it doesn't fall back to auto-derivation
    */
  implicit def someFormat[T](implicit f: DynamoFormat[T]) = new DynamoFormat[Some[T]] {
    def read(av: AttributeValue): Either[DynamoReadError, Some[T]] = {
      Option(av).map(f.read(_).map(Some(_))).getOrElse(Left[DynamoReadError, Some[T]](MissingProperty))
    }

    def write(t: Some[T]): AttributeValue = f.write(t.get)
  }
}
