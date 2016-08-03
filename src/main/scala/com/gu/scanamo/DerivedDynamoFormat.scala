package com.gu.scanamo

import cats.data.{NonEmptyList, Validated, Xor}
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.error.{DynamoReadError, InvalidPropertiesError, MissingProperty, PropertyReadError}
import shapeless._
import shapeless.labelled._

import collection.convert.decorateAll._

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
          x.leftMap(e => InvalidPropertiesError(NonEmptyList(PropertyReadError(fieldName, e), Nil))).toValidated

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
