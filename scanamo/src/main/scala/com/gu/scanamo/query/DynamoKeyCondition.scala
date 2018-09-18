package com.gu.scanamo.query

import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.syntax.Bounds

case class KeyEquals[V: DynamoFormat](key: Symbol, v: V) {
  def and[R: DynamoFormat](equalsKeyCondition: KeyEquals[R]) =
    AndEqualsCondition(this, equalsKeyCondition)
  def and[R: DynamoFormat](rangeKeyCondition: RangeKeyCondition[R]) =
    AndQueryCondition(this, rangeKeyCondition)

  def descending = Descending(this)
}

case class AndEqualsCondition[H: UniqueKeyCondition, R: UniqueKeyCondition](
  hashEquality: H,
  rangeEquality: R
)

case class Descending[T: QueryableKeyCondition](queryCondition: T)

case class AndQueryCondition[H: DynamoFormat, R: DynamoFormat](
  hashCondition: KeyEquals[H],
  rangeCondition: RangeKeyCondition[R]
) {
  def descending = Descending(this)
}

sealed abstract class RangeKeyCondition[V: DynamoFormat] extends Product with Serializable {
  val key: AttributeName
  def attributes: Map[String, V]
  def keyConditionExpression(s: String): String
}

sealed abstract class DynamoOperator(val op: String) extends Product with Serializable
final case object LT                                 extends DynamoOperator("<")
final case object LTE                                extends DynamoOperator("<=")
final case object GT                                 extends DynamoOperator(">")
final case object GTE                                extends DynamoOperator(">=")

final case class KeyIs[V: DynamoFormat](key: AttributeName, operator: DynamoOperator, v: V)
    extends RangeKeyCondition[V] {
  val placeholder                                        = "keyIsValue"
  override def keyConditionExpression(s: String): String = s"#${key.placeholder(s)} ${operator.op} :$placeholder"
  override def attributes                                = Map(placeholder -> v)
}

final case class BeginsWith[V: DynamoFormat](key: AttributeName, v: V) extends RangeKeyCondition[V] {
  val placeholder                                        = "beginsWithValue"
  override def keyConditionExpression(s: String): String = s"begins_with(#${key.placeholder(s)}, :$placeholder)"
  override def attributes                                = Map(placeholder -> v)
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
