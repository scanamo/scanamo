package com.gu.scanamo

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
  * Map(foo -> List(1, 2, 3), bar -> List(3, 2, 1))
  * }}}
  *
  * Also supports automatic derivation for case classes
  *
  * {{{
  * >>> case class Foo(a: String, b: Long)
  * >>> val fooF = DynamoFormat[Foo]
  * >>> fooF.read(fooF.write(Foo("x", 42L)))
  * Foo(x,42)
  * }}}
  */
@typeclass trait DynamoFormat[T] {
  def read(av: AttributeValue): T
  def write(t: T): AttributeValue
}

object DynamoFormat extends DerivedDynamoFormat {
  def format[T](decode: AttributeValue => T)(encode: AttributeValue => T => AttributeValue): DynamoFormat[T] = {
    new DynamoFormat[T] {
      override def read(item: AttributeValue): T = decode(item)
      override def write(t: T): AttributeValue = encode(new AttributeValue())(t)
    }
  }
  def xmap[T, U](f: DynamoFormat[T])(r: T => U)(w: U => T) = new DynamoFormat[U] {
    override def read(item: AttributeValue): U = r(f.read(item))
    override def write(t: U): AttributeValue = f.write(w(t))
  }

  /**
    * prop> (s: String) => DynamoFormat[String].read(DynamoFormat[String].write(s)) == s
    */
  implicit val stringFormat = format(_.getS)(_.withS)

  implicit val javaBooleanFormat = format[java.lang.Boolean](_.getBOOL)(_.withBOOL)

  /**
    * prop> (b: Boolean) => DynamoFormat[Boolean].read(DynamoFormat[Boolean].write(b)) == b
    */
  implicit val booleanFormat = xmap(javaBooleanFormat)(Boolean.unbox)(Boolean.box)

  /**
    * prop> (l: Long) => DynamoFormat[Long].read(DynamoFormat[Long].write(l)) == l
    */
  implicit val longFormat = format[Long](_.getN.toLong)(av => l => av.withN(l.toString))
  /**
    * prop> (i: Int) => DynamoFormat[Int].read(DynamoFormat[Int].write(i)) == i
    */
  implicit val intFormat = format[Int](_.getN.toInt)(av => i => av.withN(i.toString))

  /**
    * prop> (l: List[String]) => DynamoFormat[List[String]].read(DynamoFormat[List[String]].write(l)) == l
    */
  implicit def listFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[List[T]] =
    format[List[T]](_.getL.asScala.map(f.read).toList)(av => l => av.withL(l.map(f.write).asJava))

  /**
    * prop> (m: Map[String, Int]) => DynamoFormat[Map[String, Int]].read(DynamoFormat[Map[String, Int]].write(m)) == m
    */
  implicit def mapFormat[V](implicit f: DynamoFormat[V]): DynamoFormat[Map[String, V]] =
    format[Map[String, V]](_.getM.asScala.toMap.mapValues(f.read))(av => m => av.withM(m.mapValues(f.write).asJava))

  /**
    * prop> (o: Option[Long]) => DynamoFormat[Option[Long]].read(DynamoFormat[Option[Long]].write(o)) == o
    */
  implicit def optionFormat[T](implicit f: DynamoFormat[T]) = format[Option[T]](
    i =>
      // Yea, `isNull` can return null. Congratulations to all involved!
      if (Option(i.isNULL).map(_.booleanValue).getOrElse(false)) {
        None
      } else {
        Some(f.read(i))
      }
  )(av => t => t.map(f.write).getOrElse(av.withNULL(true)))
}

trait DerivedDynamoFormat {
  implicit val hnil: DynamoFormat[HNil] =
    new DynamoFormat[HNil] {
      def read(av: AttributeValue): HNil = HNil
      def write(t: HNil): AttributeValue = new AttributeValue().withM(Map.empty.asJava)
    }

  implicit def hcons[K <: Symbol, V, T <: HList](implicit
    headFormat: Lazy[DynamoFormat[V]],
    tailFormat: Lazy[DynamoFormat[T]],
    fieldWitness: Witness.Aux[K]
  ): DynamoFormat[FieldType[K, V] :: T] =
    new DynamoFormat[FieldType[K, V] :: T] {
      def read(av: AttributeValue): FieldType[K, V] :: T =
        field[K](headFormat.value.read(av.getM.asScala(fieldWitness.value.name))) :: tailFormat.value.read(av)

      def write(t: FieldType[K, V] :: T): AttributeValue = {
        val tailValue = tailFormat.value.write(t.tail)
        tailValue.withM((tailValue.getM.asScala + (fieldWitness.value.name -> headFormat.value.write(t.head))).asJava)
      }
    }

  implicit def generic[T, R](implicit gen: LabelledGeneric.Aux[T, R], formatR: Lazy[DynamoFormat[R]]): DynamoFormat[T] =
    new DynamoFormat[T] {
      def read(av: AttributeValue): T = gen.from(formatR.value.read(av))
      def write(t: T): AttributeValue = formatR.value.write(gen.to(t))
    }
}