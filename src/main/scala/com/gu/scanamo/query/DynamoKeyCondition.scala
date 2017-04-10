package com.gu.scanamo.query

import com.gu.scanamo.DynamoFormat

case class KeyEquals[V: DynamoFormat](key: Symbol, v: V) {
  def and[R: DynamoFormat](equalsKeyCondition: KeyEquals[R]) =
    AndEqualsCondition(this, equalsKeyCondition)
  def and[R: DynamoFormat](rangeKeyCondition: RangeKeyCondition[R]) =
    AndQueryCondition(this, rangeKeyCondition)

  def descending = Descending(this)
}

case class AndEqualsCondition[H: UniqueKeyCondition, R: UniqueKeyCondition](
  hashEquality: H, rangeEquality: R
)

case class Descending[T: QueryableKeyCondition](queryCondition: T)

case class AndQueryCondition[H: DynamoFormat, R: DynamoFormat](
  hashCondition: KeyEquals[H], rangeCondition: RangeKeyCondition[R]
) {
  def descending = Descending(this)
}

sealed abstract class RangeKeyCondition[V](implicit f: DynamoFormat[V]) extends Product with Serializable {
  val key: Symbol
  val v: V
  def keyConditionExpression(s: String): String
}

sealed abstract class DynamoOperator(val op: String) extends Product with Serializable
final case object LT  extends DynamoOperator("<")
final case object LTE extends DynamoOperator("<=")
final case object GT  extends DynamoOperator(">")
final case object GTE extends DynamoOperator(">=")

final case class KeyIs[V: DynamoFormat](key: Symbol, operator: DynamoOperator, v: V) extends RangeKeyCondition[V]{
  override def keyConditionExpression(s: String): String = s"#$s ${operator.op} :${key.name}"
}

final case class BeginsWith[V: DynamoFormat](key: Symbol, v: V) extends RangeKeyCondition[V] {
  override def keyConditionExpression(s: String): String = s"begins_with(#$s, :${key.name})"
}

final case class AttributeExists(key: Symbol)
final case class Not[T: ConditionExpression](condition: T)

