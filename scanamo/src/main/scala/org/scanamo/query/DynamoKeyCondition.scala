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

package org.scanamo.query

import org.scanamo.DynamoFormat
import org.scanamo.syntax.Bounds

case class KeyEquals[V: DynamoFormat](key: AttributeName, v: V) {
  def and[R: DynamoFormat](equalsKeyCondition: KeyEquals[R]): AndEqualsCondition[KeyEquals[V], KeyEquals[R]] =
    AndEqualsCondition(this, equalsKeyCondition)
  def and[R: DynamoFormat](rangeKeyCondition: RangeKeyCondition[R]): AndQueryCondition[V, R] =
    AndQueryCondition(this, rangeKeyCondition)
}

case class AndEqualsCondition[H: UniqueKeyCondition, R: UniqueKeyCondition](
  hashEquality: H,
  rangeEquality: R
)

case class AndQueryCondition[H: DynamoFormat, R: DynamoFormat](
  hashCondition: KeyEquals[H],
  rangeCondition: RangeKeyCondition[R]
)

sealed abstract class RangeKeyCondition[V: DynamoFormat] extends Product with Serializable {
  val key: AttributeName
  def attributes: Map[String, V]
  def keyConditionExpression(s: String): String
}

sealed abstract class DynamoOperator(val op: String) extends Product with Serializable
case object LT extends DynamoOperator("<")
case object LTE extends DynamoOperator("<=")
case object GT extends DynamoOperator(">")
case object GTE extends DynamoOperator(">=")

final case class KeyIs[V: DynamoFormat](key: AttributeName, operator: DynamoOperator, v: V)
    extends RangeKeyCondition[V] {
  val placeholder = "keyIsValue"
  override def keyConditionExpression(s: String): String = s"#${key.placeholder(s)} ${operator.op} :$placeholder"
  override def attributes = Map(placeholder -> v)
}

final case class BeginsWith[V: DynamoFormat](key: AttributeName, v: V) extends RangeKeyCondition[V] {
  val placeholder = "beginsWithValue"
  override def keyConditionExpression(s: String): String = s"begins_with(#${key.placeholder(s)}, :$placeholder)"
  override def attributes = Map(placeholder -> v)
}

final case class Between[V: DynamoFormat](key: AttributeName, bounds: Bounds[V]) extends RangeKeyCondition[V] {
  override def keyConditionExpression(s: String): String = s"#${key.placeholder(s)} BETWEEN :lower AND :upper"
  override def attributes = Map(
    "lower" -> bounds.lowerBound.v,
    "upper" -> bounds.upperBound.v
  )
}

final case class AttributeExists(key: AttributeName)
final case class AttributeNotExists(key: AttributeName)
final case class Not[T: ConditionExpression](condition: T)
