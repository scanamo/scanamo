package com.gu.scanamo.query

import com.gu.scanamo.DynamoFormat

case class KeyEquals[V: DynamoFormat](key: Symbol, v: V) {
  def and[R: DynamoFormat](equalsKeyCondition: KeyEquals[R]) =
    AndEqualsCondition(this, equalsKeyCondition)
  def and[R: DynamoFormat](rangeKeyCondition: RangeKeyCondition[R]) =
    AndQueryCondition(this, rangeKeyCondition)
}

case class AndEqualsCondition[H: UniqueKeyCondition, R: UniqueKeyCondition](
  hashEquality: H, rangeEquality: R
)

case class AndQueryCondition[H: DynamoFormat, R: DynamoFormat](
  hashCondition: KeyEquals[H], rangeCondition: RangeKeyCondition[R]
)

sealed abstract class RangeKeyCondition[V](implicit f: DynamoFormat[V]) {
  val key: Symbol
  val v: V
  def keyConditionExpression(s: String): String
}

sealed abstract class DynamoOperator(val op: String)
object LT  extends DynamoOperator("<")
object LTE extends DynamoOperator("<=")
object GT  extends DynamoOperator(">")
object GTE extends DynamoOperator(">=")

case class KeyIs[V: DynamoFormat](key: Symbol, operator: DynamoOperator, v: V) extends RangeKeyCondition[V]{
  override def keyConditionExpression(s: String): String = s"#$s ${operator.op} :${key.name}"
}

case class KeyBeginsWith[V: DynamoFormat](key: Symbol, v: V) extends RangeKeyCondition[V] {
  override def keyConditionExpression(s: String): String = s"begins_with(#$s, :${key.name})"
}
