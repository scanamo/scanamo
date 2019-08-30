package org.scanamo

import cats.data.{ Validated, ValidatedNel }
import cats.syntax.either._
import org.scanamo.error._
import org.scanamo.export.Exported
import shapeless._
import shapeless.labelled._

trait DerivedDynamoFormat {
  type FieldName = String
  type ValidatedPropertiesError[T] = ValidatedNel[(FieldName, DynamoReadError), T]

  trait ConstructedDynamoFormat[T] {
    def read(av: DynamoObject): ValidatedPropertiesError[T]
    def write(t: T): DynamoObject
  }

  trait InvalidConstructedDynamoFormat[T] extends ConstructedDynamoFormat[T]
  trait ValidConstructedDynamoFormat[T] extends ConstructedDynamoFormat[T]
  trait CoProductDynamoFormat[T] extends ConstructedDynamoFormat[T]

  implicit val hnil: InvalidConstructedDynamoFormat[HNil] =
    new InvalidConstructedDynamoFormat[HNil] {
      final def read(av: DynamoObject) = Validated.valid(HNil)
      final def write(t: HNil) = DynamoObject.empty
    }

  implicit def hcons[K <: Symbol, V, T <: HList](
    implicit
    headFormat0: Lazy[DynamoFormat[V]],
    tailFormat: ConstructedDynamoFormat[T],
    fieldWitness: Witness.Aux[K]
  ): ValidConstructedDynamoFormat[FieldType[K, V] :: T] =
    new ValidConstructedDynamoFormat[FieldType[K, V] :: T] {
      private[this] val fieldName = fieldWitness.value.name
      private[this] val headFormat = headFormat0.value

      final def read(av: DynamoObject): ValidatedPropertiesError[FieldType[K, V] :: T] = {
        val head = av(fieldName)
          .fold(headFormat.read(DynamoValue.nil).leftMap[DynamoReadError](_ => MissingProperty))(headFormat.read(_))
          .leftMap(fieldName -> _)
          .toValidatedNel
          .map(field[K](_))
        val tail = tailFormat.read(av)

        cats.Apply[ValidatedPropertiesError].map2(head, tail)(_ :: _)
      }

      final def write(t: FieldType[K, V] :: T) = {
        val tailValue = tailFormat.write(t.tail)
        val av = headFormat.write(t.head)
        if (av.isNull)
          tailValue
        else
          DynamoObject.singleton(fieldName, av) <> tailValue
      }
    }

  implicit val cnil: CoProductDynamoFormat[CNil] = new CoProductDynamoFormat[CNil] {
    final def read(av: DynamoObject) =
      Validated.invalidNel(("CNil", TypeCoercionError(new Exception(s"$av was not of the expected type"))))

    final def write(t: CNil) = sys.error("CNil cannot be written to an DynamoObject")
  }

  implicit def coproduct[K <: Symbol, V, T <: Coproduct](
    implicit
    headFormat0: Lazy[DynamoFormat[V]],
    tailFormat: CoProductDynamoFormat[T],
    fieldWitness: Witness.Aux[K]
  ): CoProductDynamoFormat[FieldType[K, V] :+: T] =
    new CoProductDynamoFormat[FieldType[K, V] :+: T] {
      private[this] val fieldName = fieldWitness.value.name
      private[this] val headFormat = headFormat0.value

      final def read(av: DynamoObject) =
        av(fieldName).fold[ValidatedPropertiesError[FieldType[K, V] :+: T]](tailFormat.read(av).map(Inr(_))) { value =>
          headFormat
            .read(value)
            .leftMap(fieldName -> _)
            .toValidatedNel
            .map(v => Inl(field[K](v)))
        }

      final def write(field: FieldType[K, V] :+: T) = field match {
        case Inl(h) =>
          DynamoObject.singleton(fieldName, headFormat.write(h))
        case Inr(t) =>
          tailFormat.write(t)
      }
    }

  implicit def genericProduct[T, R](
    implicit gen: LabelledGeneric.Aux[T, R],
    formatR: Lazy[ValidConstructedDynamoFormat[R]]
  ): Exported[DynamoFormat[T]] =
    Exported(
      new DynamoFormat[T] {
        final def read(av: DynamoValue) =
          av.asObject.fold(Either.left[DynamoReadError, T](NoPropertyOfType("M", av)))(
            formatR.value.read(_).map(gen.from).toEither.leftMap(InvalidPropertiesError(_))
          )
        final def write(t: T): DynamoValue =
          formatR.value.write(gen.to(t)).toDynamoValue
      }
    )

  implicit def genericCoProduct[T, R](
    implicit gen: LabelledGeneric.Aux[T, R],
    formatR: Lazy[CoProductDynamoFormat[R]]
  ): Exported[DynamoFormat[T]] =
    Exported(
      new DynamoFormat[T] {
        final def read(av: DynamoValue) =
          av.asObject.fold(Either.left[DynamoReadError, T](NoPropertyOfType("M", av)))(
            formatR.value.read(_).map(gen.from).toEither.leftMap(InvalidPropertiesError(_))
          )
        final def write(t: T): DynamoValue =
          formatR.value.write(gen.to(t)).toDynamoValue
      }
    )
}
