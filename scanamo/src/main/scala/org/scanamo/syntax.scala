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

package org.scanamo

import org.scanamo.query._
import org.scanamo.update._
import scala.language.implicitConversions

object syntax {
  implicit final class AsDynamoValue[A](private val x: A) extends AnyVal {
    def asDynamoValue(implicit A: DynamoFormat[A]): DynamoValue = A.write(x)
  }

  implicit class AttributeNameKeyCondition(s: String) {
    def and(other: String) = HashAndRangeKeyNames(AttributeName.of(s), AttributeName.of(other))
  }

  case class HashAndRangeKeyNames(hash: AttributeName, range: AttributeName)

  implicit def stringTupleToUniqueKey[V: DynamoFormat](pair: (String, V)) =
    UniqueKey(KeyEquals(AttributeName.of(pair._1), pair._2))

  implicit def stringTupleToKeyCondition[V: DynamoFormat](pair: (String, V)) =
    KeyEquals(AttributeName.of(pair._1), pair._2)

  implicit def toUniqueKey[T: UniqueKeyCondition](t: T) = UniqueKey(t)

  implicit def stringListTupleToUniqueKeys[V: DynamoFormat](pair: (String, Set[V])) =
    UniqueKeys(KeyList(AttributeName.of(pair._1), pair._2))

  implicit def toMultipleKeyList[H: DynamoFormat, R: DynamoFormat](pair: (HashAndRangeKeyNames, Set[(H, R)])) =
    UniqueKeys(MultipleKeyList(pair._1.hash -> pair._1.range, pair._2))

  implicit def stringTupleToQuery[V: DynamoFormat](pair: (String, V)) =
    Query(KeyEquals(AttributeName.of(pair._1), pair._2))

  implicit def toQuery[T: QueryableKeyCondition](t: T) = Query(t)

  def attributeExists(string: String) = AttributeExists(AttributeName.of(string))

  def attributeNotExists(string: String) = AttributeNotExists(AttributeName.of(string))

  def not[T: ConditionExpression](t: T) = Not(t)

  implicit class AndConditionExpression[X: ConditionExpression](x: X) {
    def and[Y: ConditionExpression](y: Y) = AndCondition(x, y)
  }

  implicit class OrConditionExpression[X: ConditionExpression](x: X) {
    def or[Y: ConditionExpression](y: Y) = OrCondition(x, y)
  }

  def set(to: String, from: String): UpdateExpression = UpdateExpression.setFromAttribute(from, to)
  def set[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression = UpdateExpression.set(fieldValue)
  def append[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression = UpdateExpression.append(fieldValue)
  def prepend[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    UpdateExpression.prepend(fieldValue)
  def appendAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
    UpdateExpression.appendAll(fieldValue)
  def prependAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
    UpdateExpression.prependAll(fieldValue)
  def add[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression = UpdateExpression.add(fieldValue)
  def delete[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression = UpdateExpression.delete(fieldValue)
  def remove(field: AttributeName): UpdateExpression = UpdateExpression.remove(field)

  implicit def stringAttributeName(s: String): AttributeName = AttributeName.of(s)
  implicit def stringAttributeNameValue[T](sv: (String, T)): (AttributeName, T) = AttributeName.of(sv._1) -> sv._2

  implicit class AndUpdateExpression(x: UpdateExpression) {
    def and(y: UpdateExpression): UpdateExpression = AndUpdate(x, y)
  }
}
