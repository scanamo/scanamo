package org.scanamo

import org.scanamo.error.{DynamoReadError, TypeCoercionError}
import org.scanamo.export.Exported
import shapeless.labelled.{FieldType, field}
import shapeless.{:+:, CNil, Coproduct, HNil, Inl, Inr, LabelledGeneric, Witness}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

abstract class EnumerationDynamoFormat[T] extends DynamoFormat[T]

/**
  * {{{
  * prop> sealed trait Animal
  * prop> case object Aardvark extends Animal
  * prop> case object Hippopotamus extends Animal
  * prop> case object Zebra extends Animal
  *
  * prop> import org.scalacheck._
  * prop> implicit val arbitraryAnimal: Arbitrary[Animal] = Arbitrary(Gen.oneOf(List(Aardvark, Hippopotamus, Zebra)))
  *
  * prop> (a: Animal) =>
  *     | DynamoFormat[Animal].read(DynamoFormat[Animal].write(a)) == Right(a)
  * }}}
  *
  * {{{
  * >>> DynamoFormat[Animal].write(Zebra).s
  * Zebra
  * }}}
  */
trait EnumDynamoFormat extends LowPriorityDynamoFormat {
  implicit val enumDynamoFormatCNil: EnumerationDynamoFormat[CNil] = new EnumerationDynamoFormat[CNil] {
    override def read(av: AttributeValue): Either[DynamoReadError, CNil] = Left(
      TypeCoercionError(new Exception(s"$av is not a recognised member of the Enumeration"))
    )
    override def write(t: CNil): AttributeValue = sys.error("Cannot write CNil")
  }

  implicit def enumDynamoFormatCCons[K <: Symbol, V, R <: Coproduct](
    implicit
    fieldWitness: Witness.Aux[K],
    emptyGeneric: LabelledGeneric.Aux[V, HNil],
    alternativeFormat: EnumerationDynamoFormat[R]
  ): EnumerationDynamoFormat[FieldType[K, V] :+: R] =
    new EnumerationDynamoFormat[FieldType[K, V] :+: R] {
      override def read(av: AttributeValue): Either[DynamoReadError, FieldType[K, V] :+: R] =
        if (av.s() == fieldWitness.value.name) Right(Inl(field[K](emptyGeneric.from(HNil))))
        else alternativeFormat.read(av).right.map(Inr(_))

      override def write(t: FieldType[K, V] :+: R): AttributeValue = t match {
        case Inl(_) => AttributeValue.builder().s(fieldWitness.value.name).build()
        case Inr(r) => alternativeFormat.write(r)
      }
    }

  implicit def enumFormat[A, Repr <: Coproduct](
    implicit
    gen: LabelledGeneric.Aux[A, Repr],
    genericFormat: EnumerationDynamoFormat[Repr]
  ): EnumerationDynamoFormat[A] =
    new EnumerationDynamoFormat[A] {
      override def read(av: AttributeValue): Either[DynamoReadError, A] = genericFormat.read(av).right.map(gen.from)
      override def write(t: A): AttributeValue = genericFormat.write(gen.to(t))
    }
}

trait LowPriorityDynamoFormat {
  implicit def dynamoFormat[T](implicit exported: Exported[DynamoFormat[T]]): DynamoFormat[T] =
    exported.instance
}
