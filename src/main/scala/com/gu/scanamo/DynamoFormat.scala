package com.gu.scanamo

import cats.Applicative
import cats.data._
import cats.std.list._
import cats.std.map._
import cats.syntax.traverse._
import cats.syntax.apply._
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import shapeless._
import shapeless.labelled._
import simulacrum.typeclass
import collection.convert.decorateAll._

/**
  * Type class for defining serialisation to and from
  * DynamoDB's `AttributeValue`
  *
  * {{{
  * >>> val mapF = DynamoFormat[Map[String, List[Int]]]
  * >>> mapF.read(mapF.write(Map("foo" -> List(1, 2, 3), "bar" -> List(3, 2, 1))))
  * Valid(Map(foo -> List(1, 2, 3), bar -> List(3, 2, 1)))
  * }}}
  *
  * Also supports automatic derivation for case classes
  *
  * {{{
  * >>> case class Foo(a: String, b: Long, c: Int)
  * >>> val fooF = DynamoFormat[Foo]
  * >>> fooF.read(fooF.write(Foo("x", 42L, 12)))
  * Valid(Foo(x,42,12))
  * }}}
  *
  * Problems reading a value are detailed
  * {{{
  * >>> case class Bar(a: String, b: String, d: Int)
  * >>> DynamoFormat[Foo].read(DynamoFormat[Bar].write(Bar("a", "b", 5)))
  * Invalid(OneAnd(NoPropertyOfType(N),List(MissingProperty(c))))
  * }}}
  */
@typeclass trait DynamoFormat[T] {
  def read(av: AttributeValue): ValidatedNel[DynamoReadError, T]
  def write(t: T): AttributeValue
}

object DynamoFormat extends DerivedDynamoFormat {
  def attribute[T](
    decode: AttributeValue => T, propertyType: String)(
    encode: AttributeValue => T => AttributeValue
  ): DynamoFormat[T] = {
    new DynamoFormat[T] {
      override def read(av: AttributeValue): ValidatedNel[DynamoReadError, T] =
        Validated.fromOption(Option(decode(av)), NonEmptyList(NoPropertyOfType(propertyType)))
      override def write(t: T): AttributeValue =
        encode(new AttributeValue())(t)
    }
  }
  def xmap[T, U](f: DynamoFormat[T])(r: T => ValidatedNel[DynamoReadError, U])(w: U => T) = new DynamoFormat[U] {
    override def read(item: AttributeValue): ValidatedNel[DynamoReadError, U] =
      f.read(item).toXor.flatMap(r(_).toXor).toValidated
    override def write(t: U): AttributeValue = f.write(w(t))
  }

  /**
    * prop> (s: String) => DynamoFormat[String].read(DynamoFormat[String].write(s)) == cats.data.Validated.valid(s)
    */
  implicit val stringFormat = attribute(_.getS, "S")(_.withS)

  implicit val javaBooleanFormat = attribute[java.lang.Boolean](_.getBOOL, "BOOL")(_.withBOOL)

  /**
    * prop> (b: Boolean) => DynamoFormat[Boolean].read(DynamoFormat[Boolean].write(b)) == cats.data.Validated.valid(b)
    */
  implicit val booleanFormat = xmap(javaBooleanFormat)(b => Validated.valid(Boolean.unbox(b)))(Boolean.box)


  val numFormat = attribute(_.getN, "N")(_.withN)
  def coerce[N](f: String => N): String => ValidatedNel[DynamoReadError, N] = s =>
    Validated.catchOnly[NumberFormatException](f(s)).leftMap(TypeCoercionError(_)).toValidatedNel
  /**
    * prop> (l: Long) => DynamoFormat[Long].read(DynamoFormat[Long].write(l)) == cats.data.Validated.valid(l)
    */
  implicit val longFormat = xmap(numFormat)(coerce(_.toLong))(_.toString)
  /**
    * prop> (i: Int) => DynamoFormat[Int].read(DynamoFormat[Int].write(i)) == cats.data.Validated.valid(i)
    */
  implicit val intFormat = xmap(numFormat)(coerce(_.toInt))(_.toString)


  val javaListFormat = attribute(_.getL, "L")(_.withL)
  /**
    * prop> (l: List[String]) => DynamoFormat[List[String]].read(DynamoFormat[List[String]].write(l)) == cats.data.Validated.valid(l)
    */
  implicit def listFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[List[T]] =
    xmap(javaListFormat)(_.asScala.toList.traverseU(f.read))(_.map(f.write).asJava)

  val javaMapFormat = attribute(_.getM, "M")(_.withM)
  /**
    * prop> (m: Map[String, Int]) => DynamoFormat[Map[String, Int]].read(DynamoFormat[Map[String, Int]].write(m)) == cats.data.Validated.valid(m)
    */
  implicit def mapFormat[V](implicit f: DynamoFormat[V]): DynamoFormat[Map[String, V]] =
    xmap(javaMapFormat)(_.asScala.toMap.traverseU(f.read))(_.mapValues(f.write).asJava)

  /**
    * prop> (o: Option[Long]) => DynamoFormat[Option[Long]].read(DynamoFormat[Option[Long]].write(o)) == cats.data.Validated.valid(o)
    */
  implicit def optionFormat[T](implicit f: DynamoFormat[T]) = new DynamoFormat[Option[T]] {
    def read(av: AttributeValue): ValidatedNel[DynamoReadError, Option[T]] = {
      // Yea, `isNull` can return null
      if (Option(av.isNULL).map(_.booleanValue).getOrElse(false)) {
        Validated.valid(None)
      } else {
        f.read(av).map(t => Some(t))
      }
    }

    def write(t: Option[T]): AttributeValue = t.map(f.write).getOrElse(new AttributeValue().withNULL(true))
  }
}

trait DerivedDynamoFormat {
  implicit val hnil: DynamoFormat[HNil] =
    new DynamoFormat[HNil] {
      def read(av: AttributeValue) = Validated.valid(HNil)
      def write(t: HNil): AttributeValue = new AttributeValue().withM(Map.empty.asJava)
    }

  implicit def hcons[K <: Symbol, V, T <: HList](implicit
    headFormat: Lazy[DynamoFormat[V]],
    tailFormat: Lazy[DynamoFormat[T]],
    fieldWitness: Witness.Aux[K]
  ): DynamoFormat[FieldType[K, V] :: T] =
    new DynamoFormat[FieldType[K, V] :: T] {
      def read(av: AttributeValue): ValidatedNel[DynamoReadError, FieldType[K, V] :: T] = {
        val fieldName = fieldWitness.value.name
        val possibleValue = Xor.fromOption(
          av.getM.asScala.get(fieldName), NonEmptyList[DynamoReadError](MissingProperty(fieldName))
        )
        val head = possibleValue.flatMap(headFormat.value.read(_).toXor).toValidated.map(field[K](_))
        val tail = tailFormat.value.read(av)
        head.map2(tail)(_ :: _)
      }
      def write(t: FieldType[K, V] :: T): AttributeValue = {
        val tailValue = tailFormat.value.write(t.tail)
        tailValue.withM((tailValue.getM.asScala + (fieldWitness.value.name -> headFormat.value.write(t.head))).asJava)
      }
    }

  implicit def generic[T, R](implicit gen: LabelledGeneric.Aux[T, R], formatR: Lazy[DynamoFormat[R]]): DynamoFormat[T] =
    new DynamoFormat[T] {
      def read(av: AttributeValue): ValidatedNel[DynamoReadError, T] = formatR.value.read(av).map(gen.from)
      def write(t: T): AttributeValue = formatR.value.write(gen.to(t))
    }
}

sealed trait DynamoReadError
case class NoPropertyOfType(propertyType: String) extends DynamoReadError
case class TypeCoercionError(e: Exception) extends DynamoReadError
case class MissingProperty(property: String) extends DynamoReadError