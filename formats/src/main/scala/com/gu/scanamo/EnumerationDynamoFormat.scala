package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.scanamo.aws.models.AmazonAttribute
import org.scanamo.error.{DynamoReadError, TypeCoercionError}
import org.scanamo.export.Exported
import org.scanamo.v1.DynamoFormatV1
import shapeless.labelled.{FieldType, field}
import shapeless.{:+:, CNil, Coproduct, HNil, Inl, Inr, LabelledGeneric, Witness}

abstract class EnumerationDynamoFormat[T] extends DynamoFormatV1[T]

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
    genericFormat: EnumerationDynamoFormat[Repr]
  ): EnumerationDynamoFormat[A] =
    new EnumerationDynamoFormat[A] {
      override def read(av: AttributeValue): Either[DynamoReadError, A] = genericFormat.read(av).right.map(gen.from)
      override def write(t: A): AttributeValue = genericFormat.write(gen.to(t))
    }
}

trait LowPriorityDynamoFormat {
  implicit def dynamoFormat[T](implicit exported: Exported[DynamoFormatV1[T]]): DynamoFormatV1[T] =
    exported.instance
}
