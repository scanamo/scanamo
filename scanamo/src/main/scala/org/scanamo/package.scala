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

package org

import org.scanamo.query._
import org.scanamo.update._

import scala.language.implicitConversions

package object scanamo {
  object syntax {
    implicit final class AsDynamoValue[A](private val x: A) extends AnyVal {
      def asDynamoValue(implicit A: DynamoFormat[A]): DynamoValue = A.write(x)
    }

    implicit class AttributeNameKeyCondition(s: String) {
      @deprecated("use `(attr, attr) =*= values` syntax", "1.0")
      def and(other: String) = HashAndRangeKeyNames(AttributeName.of(s), AttributeName.of(other))
    }

    implicit class AttributeNameTuple(s: (String, String)) {
      def =*=[H: DynamoFormat, R: DynamoFormat](values: Set[(H, R)]): MultipleKeyList[H, R] =
        MultipleKeyList((AttributeName.of(s._1), AttributeName.of(s._2)), values)
    }

    case class HashAndRangeKeyNames(hash: AttributeName, range: AttributeName)

    @deprecated("use `attr === value` syntax", "1.0")
    implicit def stringTupleToUniqueKey[V: DynamoFormat](pair: (String, V)): UniqueKey[KeyEquals[V]] =
      UniqueKey(KeyEquals(AttributeName.of(pair._1), pair._2))

    @deprecated("use `attr === value` syntax", "1.0")
    implicit def stringTupleToKeyCondition[V: DynamoFormat](pair: (String, V)): KeyEquals[V] =
      KeyEquals(AttributeName.of(pair._1), pair._2)

    implicit def toUniqueKey[T: UniqueKeyCondition](t: T): UniqueKey[T] = UniqueKey(t)

    implicit def toUniqueKeys[T: UniqueKeyConditions](t: T): UniqueKeys[T] = UniqueKeys(t)

    @deprecated("use `attr in values` syntax", "1.0")
    implicit def stringListTupleToUniqueKeys[V: DynamoFormat](pair: (String, Set[V])): UniqueKeys[KeyList[V]] =
      UniqueKeys(KeyList(AttributeName.of(pair._1), pair._2))

    implicit def toMultipleKeyList[H: DynamoFormat, R: DynamoFormat](
      pair: (HashAndRangeKeyNames, Set[(H, R)])
    ): UniqueKeys[MultipleKeyList[H, R]] =
      UniqueKeys(MultipleKeyList(pair._1.hash -> pair._1.range, pair._2))

    @deprecated("use `attr === value` syntax", "1.0")
    implicit def stringTupleToQuery[V: DynamoFormat](pair: (String, V)): Query[KeyEquals[V]] =
      Query(KeyEquals(AttributeName.of(pair._1), pair._2))

    implicit def toQuery[T: QueryableKeyCondition](t: T): Query[T] = Query(t)

    def attributeExists(string: String): AttributeExists = AttributeExists(AttributeName.of(string))

    def attributeNotExists(string: String): AttributeNotExists = AttributeNotExists(AttributeName.of(string))

    def not[T: ConditionExpression](t: T): Not[T] = Not(t)

    implicit class AndConditionExpression[X: ConditionExpression](x: X) {
      def and[Y: ConditionExpression](y: Y): AndCondition[X, Y] = AndCondition(x, y)
    }

    implicit class OrConditionExpression[X: ConditionExpression](x: X) {
      def or[Y: ConditionExpression](y: Y): OrCondition[X, Y] = OrCondition(x, y)
    }

    /** Syntax for `AndEqualsCondition` instances over a pair of `KeyEquals` with an `and` method to support
      * construction of index keys of 3 properties.
      */
    implicit class AndEqualsConditionOps[H, S](ae: AndEqualsCondition[KeyEquals[H], KeyEquals[S]]) {
      def and[I](ke: KeyEquals[I]): IndexKey3[KeyEquals[H], KeyEquals[S], KeyEquals[I]] =
        IndexKey3(ae.hashEquality, ae.rangeEquality, ke)
    }

    @deprecated("use uncurried `set(attr, value)` syntax", "1.0")
    def set[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression = UpdateExpression.set(fieldValue)
    def set[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression = UpdateExpression.set(attr, value)

    @deprecated("use uncurried `append(attr, value)` syntax", "1.0")
    def append[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression = UpdateExpression.append(fieldValue)
    def append[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression = UpdateExpression.append(attr, value)

    @deprecated("use uncurried `prepend(attr, value)` syntax", "1.0")
    def prepend[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
      UpdateExpression.prepend(fieldValue)
    def prepend[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression =
      UpdateExpression.prepend(attr, value)

    @deprecated("use uncurried `appendAll(attr, value)` syntax", "1.0")
    def appendAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
      UpdateExpression.appendAll(fieldValue)
    def appendAll[V: DynamoFormat](attr: AttributeName, value: List[V]): UpdateExpression =
      UpdateExpression.appendAll(attr, value)

    @deprecated("use uncurried `prependAll(attr, value)` syntax", "1.0")
    def prependAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
      UpdateExpression.prependAll(fieldValue)
    def prependAll[V: DynamoFormat](attr: AttributeName, value: List[V]): UpdateExpression =
      UpdateExpression.prependAll(attr, value)

    @deprecated("use uncurried `add(attr, value)` syntax", "1.0")
    def add[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression = UpdateExpression.add(fieldValue)
    def add[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression = UpdateExpression.add(attr, value)

    @deprecated("use uncurried `delete(attr, value)` syntax", "1.0")
    def delete[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression = UpdateExpression.delete(fieldValue)
    def delete[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression = UpdateExpression.delete(attr, value)

    def setFrom(to: AttributeName, from: AttributeName): UpdateExpression = UpdateExpression.setFromAttribute(from, to)
    def remove(field: AttributeName): UpdateExpression = UpdateExpression.remove(field)
    def setIfNotExists[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression =
      UpdateExpression.setIfNotExists(attr, value)

    implicit def stringAttributeName(s: String): AttributeName = AttributeName.of(s)
    implicit def stringAttributeNameValue[T](sv: (String, T)): (AttributeName, T) = AttributeName.of(sv._1) -> sv._2
  }
}
