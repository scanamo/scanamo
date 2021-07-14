/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo.generic

import cats.data.NonEmptyChain
import cats.syntax.bifunctor._
import cats.syntax.parallel._
import org.scanamo.{ DynamoFormat, DynamoObject, DynamoValue }
import org.scanamo._
import magnolia1._

private[scanamo] trait Derivation {
  type Typeclass[A] = DynamoFormat[A]

  type FieldName = String
  type Valid[A] = Either[NonEmptyChain[(FieldName, DynamoReadError)], A]

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
          param.default.fold(param.typeclass.read(DynamoValue.nil).leftMap(_ => MissingProperty))(Right(_))
        }(param.typeclass.read(_))
        .leftMap(e => NonEmptyChain.one(param.label -> e))

    // case objects are inlined as strings
    if (cc.isObject)
      new DynamoFormat[T] {
        private[this] val _cachedAttribute = DynamoValue.fromString(cc.typeName.short)
        private[this] val _cachedHit = Right(cc.rawConstruct(Nil))

        def read(dv: DynamoValue): Either[DynamoReadError, T] =
          dv.asString
            .filter(_ == cc.typeName.short)
            .fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("S", dv)))(_ => _cachedHit)

        def write(t: T): DynamoValue = _cachedAttribute
      }
    else
      new DynamoFormat.ObjectFormat[T] {
        def readObject(o: DynamoObject): Either[DynamoReadError, T] =
          cc.parameters.toList
            .parTraverse(decodeField(o, _))
            .bimap(
              es => InvalidPropertiesError(es.toNonEmptyList),
              xs => cc.rawConstruct(xs)
            )

        def writeObject(t: T): DynamoObject =
          DynamoObject(cc.parameters.foldLeft(List.empty[(String, DynamoValue)]) { case (xs, p) =>
            val v = p.typeclass.write(p.dereference(t))
            if (v.isNull) xs else (p.label -> v) :: xs
          }: _*)
      }
  }

  // Derivation for ADTs, they are encoded as an object of one property, the key being the case name
  def dispatch[T](st: SealedTrait[Typeclass, T]): Typeclass[T] = {
    def decode(o: DynamoObject): Either[DynamoReadError, T] =
      (for {
        subtype <- st.subtypes.find(sub => o.contains(sub.typeName.short))
        value <- o(subtype.typeName.short)
      } yield subtype.typeclass.read(value)).getOrElse(Left(NoPropertyOfType("M", DynamoValue.nil)))

    new DynamoFormat.ObjectFormat[T] {
      def readObject(o: DynamoObject): Either[DynamoReadError, T] = decode(o)

      def writeObject(t: T): DynamoObject =
        st.dispatch(t) { subtype =>
          DynamoObject.singleton(subtype.typeName.short, subtype.typeclass.write(subtype.cast(t)))
        }
    }
  }
}
