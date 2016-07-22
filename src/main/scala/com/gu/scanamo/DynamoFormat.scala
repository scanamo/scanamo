package com.gu.scanamo

import cats.NotNull
import cats.data._
import cats.std.list._
import cats.std.vector._
import cats.std.map._
import cats.syntax.traverse._
import cats.syntax.apply._
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.error._
import shapeless._
import shapeless.labelled._
import simulacrum.typeclass

import collection.convert.decorateAll._
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
  * Problems reading a value are detailed
  * {{{
  * >>> case class Developer(name: String, age: String, problems: Int)
  * >>> val invalid = DynamoFormat[Farmer].read(DynamoFormat[Developer].write(Developer("Alice", "none of your business", 99)))
  * >>> invalid
  * Left(InvalidPropertiesError(OneAnd(PropertyReadError(age,NoPropertyOfType(N,{S: none of your business,})),List(PropertyReadError(farm,MissingProperty)))))
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
  * Custom formats can often be most easily defined using [[DynamoFormat.coercedXmap]] or [[DynamoFormat.xmap]]
  */
@typeclass trait DynamoFormat[T] {
  def read(av: AttributeValue): Xor[DynamoReadError, T]
  def write(t: T): AttributeValue
  def default: Option[T] = None
}

object DynamoFormat extends DerivedDynamoFormat {
  private def attribute[T](
    decode: AttributeValue => T, propertyType: String)(
    encode: AttributeValue => T => AttributeValue
  ): DynamoFormat[T] = {
    new DynamoFormat[T] {
      override def read(av: AttributeValue): Xor[DynamoReadError, T] =
        Xor.fromOption(Option(decode(av)), NoPropertyOfType(propertyType, av))
      override def write(t: T): AttributeValue =
        encode(new AttributeValue())(t)
    }
  }

  /**
    * {{{
    * >>> import org.joda.time._
    * >>> import cats.data.Xor
    * >>> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    *
    * >>> implicit val jodaLongFormat = DynamoFormat.xmap[DateTime, Long](
    * ...   l => Xor.right(new DateTime(l).withZone(DateTimeZone.UTC))
    * ... )(
    * ...   _.withZone(DateTimeZone.UTC).getMillis
    * ... )
    * >>> DynamoFormat[DateTime].read(new AttributeValue().withN("0"))
    * Right(1970-01-01T00:00:00.000Z)
    * }}}
    */
  def xmap[A, B](r: B => Xor[DynamoReadError, A])(w: A => B)(implicit f: DynamoFormat[B]) = new DynamoFormat[A] {
    override def read(item: AttributeValue): Xor[DynamoReadError, A] = f.read(item).flatMap(r)
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
    *     | DynamoFormat[String].read(DynamoFormat[String].write(s)) == cats.data.Xor.right(s)
    * }}}
    */
  implicit val stringFormat = attribute(_.getS, "S")(_.withS)

  private val javaBooleanFormat = attribute[java.lang.Boolean](_.getBOOL, "BOOL")(_.withBOOL)

  /**
    * {{{
    * prop> (b: Boolean) =>
    *     | DynamoFormat[Boolean].read(DynamoFormat[Boolean].write(b)) == cats.data.Xor.right(b)
    * }}}
    */
  implicit val booleanFormat = xmap[Boolean, java.lang.Boolean](
    b => Xor.right(Boolean.unbox(b)))(
    Boolean.box
  )(javaBooleanFormat)


  private val numFormat = attribute(_.getN, "N")(_.withN)
  private def coerceNumber[N](f: String => N): String => Xor[DynamoReadError, N] =
    coerce[String, N, NumberFormatException](f)

  private def coerce[A, B, T >: scala.Null <: scala.Throwable](f: A => B)(implicit T: ClassTag[T], NT: NotNull[T]): A => Xor[DynamoReadError, B] = a =>
    Xor.catchOnly[T](f(a)).leftMap(TypeCoercionError(_))

  /**
    * {{{
    * prop> (l: Long) =>
    *     | DynamoFormat[Long].read(DynamoFormat[Long].write(l)) == cats.data.Xor.right(l)
    * }}}
    */
  implicit val longFormat = xmap(coerceNumber(_.toLong))(_.toString)(numFormat)
  /**
    * {{{
    * prop> (i: Int) =>
    *     | DynamoFormat[Int].read(DynamoFormat[Int].write(i)) == cats.data.Xor.right(i)
    * }}}
    */
  implicit val intFormat = xmap(coerceNumber(_.toInt))(_.toString)(numFormat)
  /**
    * {{{
    * prop> (d: Double) =>
    *     | DynamoFormat[Double].read(DynamoFormat[Double].write(d)) == cats.data.Xor.right(d)
    * }}}
    */
  implicit val doubleFormat = xmap(coerceNumber(_.toDouble))(_.toString)(numFormat)
  /**
    * {{{
    * prop> (s: Short) =>
    *     | DynamoFormat[Short].read(DynamoFormat[Short].write(s)) == cats.data.Xor.right(s)
    * }}}
    */
  implicit val shortFormat = xmap(coerceNumber(_.toShort))(_.toString)(numFormat)


  val javaListFormat = attribute(_.getL, "L")(_.withL)
  /**
    * {{{
    * prop> (l: List[String]) =>
    *     | DynamoFormat[List[String]].read(DynamoFormat[List[String]].write(l)) ==
    *     |   cats.data.Xor.right(l)
    * }}}
    */
  implicit def listFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[List[T]] =
    xmap[List[T], java.util.List[AttributeValue]](
      _.asScala.toList.traverseU(f.read))(
      _.map(f.write).asJava
    )(javaListFormat)

  /**
    * {{{
    * prop> (l: Vector[String]) =>
    *     | DynamoFormat[Vector[String]].read(DynamoFormat[Vector[String]].write(l)) ==
    *     |   cats.data.Xor.right(l)
    * }}}
    */
  implicit def vectorFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Vector[T]] =
    xmap[Vector[T], java.util.List[AttributeValue]](
      _.asScala.toVector.traverseU(f.read))(
      _.map(f.write).asJava
    )(javaListFormat)


  private val javaNumSetFormat = attribute(_.getNS, "NS")(_.withNS)
  private val javaStringSetFormat = attribute(_.getSS, "SS")(_.withSS)
  private def setFormat[T](r: String => Xor[DynamoReadError, T])(w: T => String)(df: DynamoFormat[java.util.List[String]]): DynamoFormat[Set[T]] =
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
    *     |   DynamoFormat[Set[Int]].read(av) == cats.data.Xor.right(s)
    * }}}
    */
  implicit val intSetFormat = setFormat(coerceNumber(_.toInt))(_.toString)(javaNumSetFormat)
  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> (s: Set[Long]) =>
    *     | val av = new AttributeValue().withNS(s.map(_.toString).toList: _*)
    *     | DynamoFormat[Set[Long]].write(s) == av &&
    *     |   DynamoFormat[Set[Long]].read(av) == cats.data.Xor.right(s)
    * }}}
    */
  implicit val longSetFormat = setFormat(coerceNumber(_.toLong))(_.toString)(javaNumSetFormat)
  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> (s: Set[Double]) =>
    *     | val av = new AttributeValue().withNS(s.map(_.toString).toList: _*)
    *     | DynamoFormat[Set[Double]].write(s) == av &&
    *     |   DynamoFormat[Set[Double]].read(av) == cats.data.Xor.right(s)
    * }}}
    */
  implicit val doubleSetFormat = setFormat(coerceNumber(_.toDouble))(_.toString)(javaNumSetFormat)
  /**
    * {{{
    * prop> import com.amazonaws.services.dynamodbv2.model.AttributeValue
    * prop> (s: Set[String]) =>
    *     | val av = new AttributeValue().withSS(s.toList: _*)
    *     | DynamoFormat[Set[String]].write(s) == av &&
    *     |   DynamoFormat[Set[String]].read(av) == cats.data.Xor.right(s)
    * }}}
    */
  implicit val stringSetFormat =
    xmap[Set[String], java.util.List[String]](
      s => Xor.right(s.asScala.toSet))(
      _.toList.asJava
    )(javaStringSetFormat)

  private val javaMapFormat = attribute(_.getM, "M")(_.withM)
  /**
    * {{{
    * prop> (m: Map[String, Int]) =>
    *     | DynamoFormat[Map[String, Int]].read(DynamoFormat[Map[String, Int]].write(m)) ==
    *     |   cats.data.Xor.right(m)
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
    *     |   cats.data.Xor.right(o)
    * }}}
    */
  implicit def optionFormat[T](implicit f: DynamoFormat[T]) = new DynamoFormat[Option[T]] {
    def read(av: AttributeValue): Xor[DynamoReadError, Option[T]] = {
      Option(av).map(f.read(_).map(Some(_)))
        .getOrElse(Xor.right(Option.empty[T]))
    }

    def write(t: Option[T]): AttributeValue = t.map(f.write).getOrElse(null)
    override val default = Some(None)
  }

  /**
    * This ensures that if, for instance, you specify an update with Some(5) rather
    * than making the type of `Option` explicit, it doesn't fall back to auto-derivation
    */
  implicit def someFormat[T](implicit f: DynamoFormat[T]) = new DynamoFormat[Some[T]] {
    def read(av: AttributeValue): Xor[DynamoReadError, Some[T]] = {
      Option(av).map(f.read(_).map(Some(_))).getOrElse(Xor.left[DynamoReadError, Some[T]](MissingProperty))
    }

    def write(t: Some[T]): AttributeValue = f.write(t.get)
  }
}

trait DerivedDynamoFormat {
  type ValidatedPropertiesError[T] = Validated[InvalidPropertiesError, T]

  trait ConstructedDynamoFormat[T] {
    def read(av: AttributeValue): Validated[InvalidPropertiesError, T]
    def write(t: T): AttributeValue
  }

  implicit val hnil: ConstructedDynamoFormat[HNil] =
    new ConstructedDynamoFormat[HNil] {
      def read(av: AttributeValue) = Validated.valid(HNil)
      def write(t: HNil): AttributeValue = new AttributeValue().withM(Map.empty[String, AttributeValue].asJava)
    }

  implicit def hcons[K <: Symbol, V, T <: HList](implicit
    headFormat: Lazy[DynamoFormat[V]],
    tailFormat: Lazy[ConstructedDynamoFormat[T]],
    fieldWitness: Witness.Aux[K]
  ): ConstructedDynamoFormat[FieldType[K, V] :: T] =
    new ConstructedDynamoFormat[FieldType[K, V] :: T] {
      def read(av: AttributeValue): Validated[InvalidPropertiesError, FieldType[K, V] :: T] = {
        val fieldName = fieldWitness.value.name

        val possibleValue = av.getM.asScala.get(fieldName).map(headFormat.value.read).orElse(headFormat.value.default.map(Xor.right))

        val validatedValue = possibleValue.getOrElse(Xor.left[DynamoReadError, V](MissingProperty))

        def withPropertyError(x: Xor[DynamoReadError, V]): Validated[InvalidPropertiesError, V] =
          x.leftMap(e => InvalidPropertiesError(NonEmptyList(PropertyReadError(fieldName, e)))).toValidated

        val head: Validated[InvalidPropertiesError, FieldType[K, V]] = withPropertyError(validatedValue).map(field[K](_))
        val tail = tailFormat.value.read(av)

        cats.Apply[ValidatedPropertiesError].map2(head, tail)(_ :: _)
      }
      def write(t: FieldType[K, V] :: T): AttributeValue = {
        val tailValue = tailFormat.value.write(t.tail)
        tailValue.withM((tailValue.getM.asScala + (fieldWitness.value.name -> headFormat.value.write(t.head))).asJava)
      }
    }

  implicit def generic[T, R](implicit gen: LabelledGeneric.Aux[T, R], formatR: Lazy[ConstructedDynamoFormat[R]]): DynamoFormat[T] =
    new DynamoFormat[T] {
      def read(av: AttributeValue): Xor[DynamoReadError, T] = formatR.value.read(av).map(gen.from).toXor
      def write(t: T): AttributeValue = formatR.value.write(gen.to(t))
    }
}

