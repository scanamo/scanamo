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
  type NotSymbol[T] = |¬|[Symbol]#λ[T]

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
    headFormat: Lazy[DynamoFormat[V]],
    tailFormat: Lazy[ConstructedDynamoFormat[T]],
    fieldWitness: Witness.Aux[K]
  ): ValidConstructedDynamoFormat[FieldType[K, V] :: T] =
    new ValidConstructedDynamoFormat[FieldType[K, V] :: T] {
      final private val fieldName = fieldWitness.value.name

      final def read(av: DynamoObject) = {
        val valueOrError: Either[(FieldName, DynamoReadError), V] =
          av(fieldName)
            .map(headFormat.value.read)
            .orElse(headFormat.value.default.map(Either.right))
            .getOrElse(Either.left(MissingProperty))
            .leftMap(((fieldName, _)))

        val head = valueOrError.toValidatedNel.map(field[K](_))
        val tail = tailFormat.value.read(av)

        cats.Apply[ValidatedPropertiesError].map2(head, tail)(_ :: _)
      }

      final def write(t: FieldType[K, V] :: T) = {
        val tailValue = tailFormat.value.write(t.tail)
        val av = headFormat.value.write(t.head)
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
    headFormat: Lazy[DynamoFormat[V]],
    tailFormat: CoProductDynamoFormat[T],
    fieldWitness: Witness.Aux[K]
  ): CoProductDynamoFormat[FieldType[K, V] :+: T] =
    new CoProductDynamoFormat[FieldType[K, V] :+: T] {
      final private val fieldName = fieldWitness.value.name

      final def read(av: DynamoObject) =
        av(fieldName).fold[ValidatedPropertiesError[FieldType[K, V] :+: T]](tailFormat.read(av).map(Inr(_))) {
          nestedAv =>
            val value = headFormat.value.read(nestedAv).toValidatedNel
            value.map(v => Inl(field[K](v))).leftMap(_.map((fieldName, _)))
        }

      final def write(field: FieldType[K, V] :+: T) = field match {
        case Inl(h) =>
          DynamoObject.singleton(fieldName, headFormat.value.write(h))
        case Inr(t) =>
          tailFormat.write(t)
      }
    }

  implicit def genericProduct[T: NotSymbol, R](
    implicit gen: LabelledGeneric.Aux[T, R],
    formatR: Lazy[ValidConstructedDynamoFormat[R]]
  ): Exported[DynamoFormat[T]] =
    Exported(
      new DynamoFormat[T] {
        final def read(av: DynamoValue) =
          av.asObject.fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("M", av)))(
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
          av.asObject.fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("M", av)))(
            formatR.value.read(_).map(gen.from).toEither.leftMap(InvalidPropertiesError(_))
          )
        final def write(t: T): DynamoValue =
          formatR.value.write(gen.to(t)).toDynamoValue
      }
    )
}
