package com.gu.scanamo

sealed abstract class DynamoOperator(val op: String)
object LT  extends DynamoOperator("<")
object LTE extends DynamoOperator("<=")
object GT  extends DynamoOperator(">")
object GTE extends DynamoOperator(">=")

case class AndEqualsCondition[H, R](hashEquality: H, rangeEquality: R)(
  implicit val hashCondition: UniqueKeyCondition[H], val rangeCondition: UniqueKeyCondition[R])

case class KeyEquals[V](key: Symbol, v: V)(implicit val f: DynamoFormat[V]) {
  def and[R](rangeKeyCondition: RangeKeyCondition[R])(implicit fR: DynamoFormat[R]) =
    AndQueryCondition(this, rangeKeyCondition)
  def and[R](equalsKeyCondition: KeyEquals[R])(implicit fR: DynamoFormat[R]) =
    AndEqualsCondition(this, equalsKeyCondition)
}
case class AndQueryCondition[H, R](hashCondition: KeyEquals[H], rangeCondition: RangeKeyCondition[R])
  (implicit val fH: DynamoFormat[H], val fR: DynamoFormat[R])

sealed abstract class RangeKeyCondition[V](implicit f: DynamoFormat[V]) {
  val key: Symbol
  val v: V
  def keyConditionExpression(s: String): String
}

case class KeyIs[V](key: Symbol, operator: DynamoOperator, v: V)(implicit f: DynamoFormat[V]) extends RangeKeyCondition[V]{
  def keyConditionExpression(s: String): String = s"#$s ${operator.op} :${key.name}"
}

case class KeyBeginsWith[V](key: Symbol, v: V)(implicit f: DynamoFormat[V]) extends RangeKeyCondition[V] {
  override def keyConditionExpression(s: String): String = s"begins_with(#$s, :${key.name})"
}
