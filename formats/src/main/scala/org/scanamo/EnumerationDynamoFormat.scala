package org.scanamo

import org.scanamo.error.{DynamoReadError, TypeCoercionError}
import org.scanamo.export.Exported
import shapeless.labelled.{field, FieldType}
import shapeless.{:+:, CNil, Coproduct, HNil, Inl, Inr, LabelledGeneric, Witness}

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
  * >>> DynamoFormat[Animal].write(Zebra).asString
  * Some(Zebra)
  * }}}
  */
trait EnumDynamoFormat extends LowPriorityDynamoFormat {
  implicit val enumDynamoFormatCNil: EnumerationDynamoFormat[CNil] = new EnumerationDynamoFormat[CNil] {
    final def read(av: DynamoValue): Either[DynamoReadError, CNil] = Left(
      TypeCoercionError(new Exception(s"$av is not a recognised member of the Enumeration"))
    )
    final def write(t: CNil): DynamoValue = sys.error("Cannot write CNil")
  }

  implicit def enumDynamoFormatCCons[K <: Symbol, V, R <: Coproduct](
    implicit
    fieldWitness: Witness.Aux[K],
    emptyGeneric: LabelledGeneric.Aux[V, HNil],
    alternativeFormat: EnumerationDynamoFormat[R]
  ): EnumerationDynamoFormat[FieldType[K, V] :+: R] =
    new EnumerationDynamoFormat[FieldType[K, V] :+: R] {
      final def read(av: DynamoValue): Either[DynamoReadError, FieldType[K, V] :+: R] =
        if (av.asString.exists(_ == fieldWitness.value.name)) Right(Inl(field[K](emptyGeneric.from(HNil))))
        else alternativeFormat.read(av).right.map(Inr(_))

      final def write(t: FieldType[K, V] :+: R): DynamoValue = t match {
        case Inl(_) => DynamoValue.string(fieldWitness.value.name)
        case Inr(r) => alternativeFormat.write(r)
      }
    }

  implicit def enumFormat[A, Repr <: Coproduct](
    implicit
    gen: LabelledGeneric.Aux[A, Repr],
    genericFormat: EnumerationDynamoFormat[Repr]
  ): EnumerationDynamoFormat[A] =
    new EnumerationDynamoFormat[A] {
      final def read(av: DynamoValue): Either[DynamoReadError, A] = genericFormat.read(av).right.map(gen.from)
      final def write(t: A): DynamoValue = genericFormat.write(gen.to(t))
    }
}

trait LowPriorityDynamoFormat {
  implicit def dynamoFormat[T](implicit exported: Exported[DynamoFormat[T]]): DynamoFormat[T] =
    exported.instance
}
