package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.QueryRequest

import collection.convert.decorateAsJava._

sealed abstract class DynamoOperator(val op: String)
object LT  extends DynamoOperator("<")
object LTE extends DynamoOperator("<=")
object GT  extends DynamoOperator(">")
object GTE extends DynamoOperator(">=")

sealed abstract class QueryableKeyCondition {
  def apply(req: QueryRequest): QueryRequest
}

case class HashKeyCondition[V](key: Symbol, v: V)(implicit f: DynamoFormat[V]) extends QueryableKeyCondition {
  def apply(req: QueryRequest): QueryRequest =
    req.withKeyConditionExpression(s"#K = :${key.name}")
      .withExpressionAttributeNames(Map("#K" -> key.name).asJava)
      .withExpressionAttributeValues(ScanamoRequest.asAVMap(Symbol(s":${key.name}") -> v))
  def and[R](rangeKeyCondition: RangeKeyCondition[R])(implicit fR: DynamoFormat[R]) =
    AndKeyCondition(this, rangeKeyCondition)
}
case class AndKeyCondition[H, R](hashCondition: HashKeyCondition[H], rangeCondition: RangeKeyCondition[R])
  (implicit fH: DynamoFormat[H], fR: DynamoFormat[R]) extends QueryableKeyCondition
{
  def apply(req: QueryRequest): QueryRequest =
    req.withKeyConditionExpression(
      s"#K = :${hashCondition.key.name} AND ${rangeCondition.keyConditionExpression("R")}"
    )
      .withExpressionAttributeNames(Map("#K" -> hashCondition.key.name, "#R" -> rangeCondition.key.name).asJava)
      .withExpressionAttributeValues(
        Map(
          s":${hashCondition.key.name}" -> fH.write(hashCondition.v),
          s":${rangeCondition.key.name}" -> fR.write(rangeCondition.v)
        ).asJava
      )
}

sealed abstract class RangeKeyCondition[V](implicit f: DynamoFormat[V]) {
  val key: Symbol
  val v: V
  def keyConditionExpression(s: String): String
}

case class SimpleKeyCondition[V](key: Symbol, v: V, operator: DynamoOperator)(implicit f: DynamoFormat[V]) extends RangeKeyCondition[V]{
  def keyConditionExpression(s: String): String = s"#$s ${operator.op} :${key.name}"
}

case class BeginsWithCondition[V](key: Symbol, v: V)(implicit f: DynamoFormat[V]) extends RangeKeyCondition[V] {
  override def keyConditionExpression(s: String): String = s"begins_with(#$s, :${key.name})"
}

object DynamoKeyCondition {
  object syntax {
    implicit class SymbolKeyCondition(s: Symbol) {
      def ===[V](v: V)(implicit f: DynamoFormat[V]) = HashKeyCondition(s, v)

      def <[V](v: V)(implicit f: DynamoFormat[V]) = SimpleKeyCondition(s, v, LT)
      def >[V](v: V)(implicit f: DynamoFormat[V]) = SimpleKeyCondition(s, v, GT)
      def <=[V](v: V)(implicit f: DynamoFormat[V]) = SimpleKeyCondition(s, v, LTE)
      def >=[V](v: V)(implicit f: DynamoFormat[V]) = SimpleKeyCondition(s, v, GTE)
      def beginsWith[V](v: V)(implicit f: DynamoFormat[V]) = BeginsWithCondition(s, v)
    }
  }
}



