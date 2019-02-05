package org.scanamo

import org.scanamo.aws.models.AmazonAttribute
import org.scanamo.error.{DynamoReadError, TypeCoercionError}
import org.scanamo.export.Exported
import shapeless.labelled.{FieldType, field}
import shapeless.{:+:, CNil, Coproduct, HNil, Inl, Inr, LabelledGeneric, Witness}

abstract class EnumerationDynamoFormat[T, AttributeValue: AmazonAttribute] extends DynamoFormat[T, AttributeValue]

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
  * >>> DynamoFormat[Animal].write(Zebra).getS
  * Zebra
  * }}}
  */
abstract class EnumDynamoFormat[AttributeValue: AmazonAttribute] extends LowPriorityDynamoFormat[AttributeValue] {
  implicit val enumDynamoFormatCNil: EnumerationDynamoFormat[CNil, AttributeValue] = new EnumerationDynamoFormat[CNil, AttributeValue] {
    override def read(av: AttributeValue): Either[DynamoReadError, CNil] = Left(
      TypeCoercionError(new Exception(s"$av is not a recognised member of the Enumeration"))
    )
    override def write(t: CNil): AttributeValue = sys.error("Cannot write CNil")
  }

  implicit def enumDynamoFormatCCons[K <: Symbol, V, R <: Coproduct](
    implicit
    fieldWitness: Witness.Aux[K],
    emptyGeneric: LabelledGeneric.Aux[V, HNil],
    alternativeFormat: EnumerationDynamoFormat[R, AttributeValue]
  ): EnumerationDynamoFormat[FieldType[K, V] :+: R, AttributeValue] =
    new EnumerationDynamoFormat[FieldType[K, V] :+: R, AttributeValue] {
      private val helper = implicitly[AmazonAttribute[AttributeValue]]
      override def read(av: AttributeValue): Either[DynamoReadError, FieldType[K, V] :+: R] = {
        if (helper.getString(av) == fieldWitness.value.name) Right(Inl(field[K](emptyGeneric.from(HNil))))
        else alternativeFormat.read(av).right.map(Inr(_))
      }

      override def write(t: FieldType[K, V] :+: R): AttributeValue = t match {
        case Inl(_) =>
          helper.setString(helper.init)(fieldWitness.value.name)
        case Inr(r) => alternativeFormat.write(r)
      }
    }

  implicit def enumFormat[A, Repr <: Coproduct](
    implicit
    gen: LabelledGeneric.Aux[A, Repr],
    genericFormat: EnumerationDynamoFormat[Repr, AttributeValue]
  ): EnumerationDynamoFormat[A, AttributeValue] =
    new EnumerationDynamoFormat[A, AttributeValue] {
      override def read(av: AttributeValue): Either[DynamoReadError, A] = genericFormat.read(av).right.map(gen.from)
      override def write(t: A): AttributeValue = genericFormat.write(gen.to(t))
    }
}

abstract class LowPriorityDynamoFormat[AttributeValue: AmazonAttribute] {
  implicit def dynamoFormat[T](implicit exported: Exported[DynamoFormat[T, AttributeValue]]): DynamoFormat[T, AttributeValue] =
    exported.instance
}
