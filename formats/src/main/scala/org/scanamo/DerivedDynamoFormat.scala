package org.scanamo

import cats.data.{NonEmptyList, Validated}
import cats.syntax.either._
import org.scanamo.error._
import org.scanamo.export.Exported
import shapeless._
import shapeless.labelled._

trait DerivedDynamoFormat {
  type ValidatedPropertiesError[T] = Validated[InvalidPropertiesError, T]
  type NotSymbol[T] = |¬|[Symbol]#λ[T]

  trait ConstructedDynamoFormat[T] {
    def read(av: DynamoValue): Validated[InvalidPropertiesError, T]
    def write(t: T): DynamoValue
  }

  trait InvalidConstructedDynamoFormat[T] extends ConstructedDynamoFormat[T]
  trait ValidConstructedDynamoFormat[T] extends ConstructedDynamoFormat[T]

  implicit val hnil: InvalidConstructedDynamoFormat[HNil] =
    new InvalidConstructedDynamoFormat[HNil] {
      final def read(av: DynamoValue) = Validated.valid(HNil)
      final def write(t: HNil): DynamoValue = DynamoValue.nil
    }

  implicit def hcons[K <: Symbol, V, T <: HList](
    implicit
    headFormat: Lazy[DynamoFormat[V]],
    tailFormat: Lazy[ConstructedDynamoFormat[T]],
    fieldWitness: Witness.Aux[K]
  ): ValidConstructedDynamoFormat[FieldType[K, V] :: T] =
    new ValidConstructedDynamoFormat[FieldType[K, V] :: T] {
      private final val fieldName = fieldWitness.value.name
      
      final def read(av: DynamoValue): Validated[InvalidPropertiesError, FieldType[K, V] :: T] = {
        val possibleValue =
          av.asObject
            .flatMap(_.get(fieldName))
            .map(headFormat.value.read)
            .orElse(headFormat.value.default.map(Either.right))

        val valueOrError = possibleValue.getOrElse(Either.left[DynamoReadError, V](MissingProperty))

        def validatedProperty(x: Either[DynamoReadError, V]): Validated[InvalidPropertiesError, V] =
          x.leftMap(e => InvalidPropertiesError(NonEmptyList(PropertyReadError(fieldName, e), Nil))).toValidated

        val head: Validated[InvalidPropertiesError, FieldType[K, V]] = validatedProperty(valueOrError).map(field[K](_))
        val tail = tailFormat.value.read(av)

        cats.Apply[ValidatedPropertiesError].map2(head, tail)(_ :: _)
      }

      final def write(t: FieldType[K, V] :: T): DynamoValue = {
        val tailValue = tailFormat.value.write(t.tail)
        val av = headFormat.value.write(t.head)
        DynamoValue.keyStore(fieldName -> av) <> tailValue
      }
    }

  trait CoProductDynamoFormat[T] extends DynamoFormat[T]

  implicit val cnil: CoProductDynamoFormat[CNil] = new CoProductDynamoFormat[CNil] {
    final def read(av: DynamoValue): Either[DynamoReadError, CNil] =
      Left(TypeCoercionError(new Exception(s"$av was not of the expected type")))

    final def write(t: CNil): DynamoValue = sys.error("CNil cannot be written to an DynamoValue")
  }

  implicit def coproduct[K <: Symbol, V, T <: Coproduct](
    implicit
    headFormat: Lazy[DynamoFormat[V]],
    tailFormat: CoProductDynamoFormat[T],
    fieldWitness: Witness.Aux[K]
  ): CoProductDynamoFormat[FieldType[K, V] :+: T] =
    new CoProductDynamoFormat[FieldType[K, V] :+: T] {
      private final val fieldName = fieldWitness.value.name

      final def read(av: DynamoValue): Either[DynamoReadError, FieldType[K, V] :+: T] =
        av.asObject.flatMap(_.get(fieldName)) match {
          case Some(nestedAv) =>
            val value = headFormat.value.read(nestedAv)
            value.map(v => Inl(field[K](v)))
          case None =>
            tailFormat.read(av).map(v => Inr(v))
        }

      final def write(field: FieldType[K, V] :+: T): DynamoValue = field match {
        case Inl(h) =>
          DynamoValue.keyStore(fieldName -> headFormat.value.write(h))
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
        final def read(av: DynamoValue): Either[DynamoReadError, T] = formatR.value.read(av).map(gen.from).toEither
        final def write(t: T): DynamoValue = formatR.value.write(gen.to(t))
      }
    )

  implicit def genericCoProduct[T, R](
    implicit gen: LabelledGeneric.Aux[T, R],
    formatR: Lazy[CoProductDynamoFormat[R]]
  ): Exported[DynamoFormat[T]] =
    Exported(
      new DynamoFormat[T] {
        final def read(av: DynamoValue): Either[DynamoReadError, T] = formatR.value.read(av).map(gen.from)
        final def write(t: T): DynamoValue = formatR.value.write(gen.to(t))
      }
    )
}
