package org.scanamo.generic

import cats.data.NonEmptyChain
import cats.instances.either._
import cats.instances.list._
import cats.instances.parallel._
import cats.syntax.bifunctor._
import cats.syntax.parallel._
import org.scanamo.{ DynamoFormat, DynamoObject, DynamoValue }
import org.scanamo._
import magnolia._

private[scanamo] trait Derivation {
  type Typeclass[A]

  protected def build[A](df: DynamoFormat[A]): Typeclass[A]

  protected def unbuild[A](tc: Typeclass[A]): DynamoFormat[A]

  type FieldName = String
  type Valid[A] = Either[NonEmptyChain[(FieldName, DynamoReadError)], A]

  // This is necessary because Magnolia will search for instances of `ExportedDynamoFormat[T]`
  // for all type members `T` of derived classes, without it it won't find instances for
  // all packaged instances defined in [[org.scanamo.DynamoFormat]]
  implicit def embed[T](implicit D: DynamoFormat[T]): Typeclass[T] = build(D)

  // Derivation for case classes: generates an encoding that is isomorphic to the
  // isomorphic n-tuple for type `T`. For case objects, they are encoded as strings.
  // 1. we look up the class field in the object
  // 2. if not found, we check if the field has a default value
  // 3. if not, we try to decode a null value and see if the formatter produces something
  //    if yes, we just produce that value as a success
  // 4. otherwise, we decode the found value
  // 5. finally, we wrap errors in [[cats.data.NonEmptyChain]] so multiple decoding errors can
  //    be accumulated
  def combine[T](cc: CaseClass[Typeclass, T]): Typeclass[T] = {
    def decodeField(o: DynamoObject, param: Param[Typeclass, T]): Valid[param.PType] =
      o(param.label)
        .fold[Either[DynamoReadError, param.PType]] {
          param.default.fold(unbuild(param.typeclass).read(DynamoValue.nil).leftMap(_ => MissingProperty))(Right(_))
        }(unbuild(param.typeclass).read(_))
        .leftMap(e => NonEmptyChain.one(param.label -> e))

    // case objects are inlined as strings
    if (cc.isObject)
      build(new DynamoFormat[T] {
        private[this] val _cachedAttribute = DynamoValue.fromString(cc.typeName.short)
        private[this] val _cachedHit = Right(cc.rawConstruct(Nil))

        def read(dv: DynamoValue): Either[DynamoReadError, T] =
          dv.asString
            .filter(_ == cc.typeName.short)
            .fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("S", dv)))(_ => _cachedHit)

        def write(t: T): DynamoValue = _cachedAttribute
      })
    else
      build(new DynamoFormat.ObjectFormat[T] {
        def readObject(o: DynamoObject): Either[DynamoReadError, T] =
          cc.parameters.toList
            .parTraverse(decodeField(o, _))
            .bimap(
              es => InvalidPropertiesError(es.toNonEmptyList),
              xs => cc.rawConstruct(xs)
            )

        def writeObject(t: T): DynamoObject =
          DynamoObject(cc.parameters.foldLeft(List.empty[(String, DynamoValue)]) {
            case (xs, p) =>
              val v = unbuild(p.typeclass).write(p.dereference(t))
              if (v.isNull) xs else (p.label -> v) :: xs
          }: _*)
      })
  }

  // Derivation for ADTs, they are encoded as an object of one property, the key being the case name
  def dispatch[T](st: SealedTrait[Typeclass, T]): Typeclass[T] = {
    def decode(o: DynamoObject): Either[DynamoReadError, T] =
      (for {
        subtype <- st.subtypes.find(sub => o.contains(sub.typeName.short))
        value <- o(subtype.typeName.short)
      } yield unbuild(subtype.typeclass).read(value)).getOrElse(Left(NoPropertyOfType("M", DynamoValue.nil)))

    build(new DynamoFormat.ObjectFormat[T] {
      def readObject(o: DynamoObject): Either[DynamoReadError, T] = decode(o)

      def writeObject(t: T): DynamoObject = st.dispatch(t) { subtype =>
        DynamoObject.singleton(subtype.typeName.short, unbuild(subtype.typeclass).write(subtype.cast(t)))
      }
    })
  }
}
