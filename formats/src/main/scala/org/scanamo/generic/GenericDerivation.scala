package org.scanamo.generic

import cats.data.NonEmptyChain
import cats.instances.either._
import cats.instances.list._
import cats.instances.parallel._
import cats.syntax.bifunctor._
import cats.syntax.parallel._
import org.scanamo.{ DynamoFormat, DynamoObject, DynamoValue, Exported, ExportedDynamoFormat }
import org.scanamo.error._
import magnolia._

private[scanamo] trait GenericDerivation {
  // This is necessary because Magnolia will search for instances of `ExportedDynamoFormat[T]`
  // for all type members `T` of derived classes, without it it won't find instances for
  // all packaged instances defined in [[org.scanamo.DynamoFormat]]
  implicit def exported[T](implicit D: DynamoFormat[T]): ExportedDynamoFormat[T] = Exported(D)

  type Typeclass[A] = ExportedDynamoFormat[A]
  type FieldName = String
  type Valid[A] = Either[NonEmptyChain[(FieldName, DynamoReadError)], A]

  // Derivation for case classes: generates an encoding that is isomorphic to the
  // isomorphic n-tuple for type `T`. For case objects, they are encoded as strings.
  def combine[T](cc: CaseClass[Typeclass, T]): Typeclass[T] = {
    def decodeField(o: DynamoObject, param: Param[Typeclass, T]): Valid[param.PType] =
      o(param.label)
        .fold[Either[DynamoReadError, param.PType]](Left(MissingProperty)) { dv =>
          param.typeclass.instance.read(dv)
        }
        .leftMap(e => NonEmptyChain.one(param.label -> e))

    def decode(o: DynamoObject): Either[DynamoReadError, T] =
      cc.parameters.toList
        .parTraverse(p => decodeField(o, p))
        .bimap(
          es => InvalidPropertiesError(es.toNonEmptyList),
          xs => cc.rawConstruct(xs)
        )

    // case objects are inlined as strings
    if (cc.isObject)
      Exported(new DynamoFormat[T] {
        private[this] val _cachedAttribute = DynamoValue.fromString(cc.typeName.short)
        private[this] val _cachedHit = Right(cc.rawConstruct(Nil))

        def read(dv: DynamoValue): Either[DynamoReadError, T] =
          dv.asString
            .filter(_ == cc.typeName.short)
            .fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("S", dv)))(_ => _cachedHit)

        def write(t: T): DynamoValue = _cachedAttribute
      })
    else
      Exported(new DynamoFormat[T] {
        def read(dv: DynamoValue): Either[DynamoReadError, T] =
          dv.asObject
            .fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("M", dv)))(decode)

        def write(t: T): DynamoValue =
          DynamoValue.fromFields(cc.parameters.map { p =>
            p.label -> p.typeclass.instance.write(p.dereference(t))
          }: _*)
      })

  }

  // Derivation for ADTs, they are encoded as an object of two properties:
  //   - `tag`: the name of the case class/object within the ADT
  //   - `value`: the encoded value for that particular case class/object
  def dispatch[T](st: SealedTrait[Typeclass, T]): Typeclass[T] = {
    def decode(o: DynamoObject): Either[DynamoReadError, T] =
      (for {
        tag <- o("tag").flatMap(_.asString)
        subtype <- st.subtypes.find(_.typeName.short == tag)
        value <- o("value")
      } yield subtype.typeclass.instance.read(value)).getOrElse(Left(NoPropertyOfType("M", DynamoValue.nil)))

    Exported(new DynamoFormat[T] {
      def read(dv: DynamoValue): Either[DynamoReadError, T] =
        dv.asObject
          .fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("M", dv)))(decode)

      def write(t: T): DynamoValue = st.dispatch(t) { subtype =>
        DynamoValue.fromFields(
          "tag" -> DynamoValue.fromString(subtype.typeName.short),
          "value" -> subtype.typeclass.instance.write(subtype.cast(t))
        )
      }
    })
  }

}
