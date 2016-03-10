package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition
import com.amazonaws.services.dynamodbv2.model.QueryRequest

import collection.convert.decorateAsJava._

sealed trait DynamoKeyCondition[V] {
  def keyConditionExpression(s: String): String
  val key: Symbol
  val v: V
  def apply(req: QueryRequest)(implicit f: DynamoFormat[V]): QueryRequest =
    req.withKeyConditionExpression(keyConditionExpression("K"))
      .withExpressionAttributeNames(Map("#K" -> key.name).asJava)
      .withExpressionAttributeValues(ScanamoRequest.asAVMap(Symbol(s":${key.name}") -> v))
}

case class EqualsKeyCondition[V](val key: Symbol, val v: V) extends DynamoKeyCondition[V] {
  override def keyConditionExpression(s: String): String = s"#$s = :${key.name}"
  def and[R](rangeKeyCondition: DynamoKeyCondition[R]) =
    AndKeyCondition(this, rangeKeyCondition)
}
case class AndKeyCondition[H, R](hashCondition: EqualsKeyCondition[H], rangeCondition: DynamoKeyCondition[R]) {
  def apply(req: QueryRequest)(implicit fH: DynamoFormat[H], fR: DynamoFormat[R]): QueryRequest =
    req.withKeyConditionExpression(
      s"${hashCondition.keyConditionExpression("K")} AND ${rangeCondition.keyConditionExpression("R")}"
    )
      .withExpressionAttributeNames(Map("#K" -> hashCondition.key.name, "#R" -> rangeCondition.key.name).asJava)
      .withExpressionAttributeValues(
        Map(
          s":${hashCondition.key.name}" -> fH.write(hashCondition.v),
          s":${rangeCondition.key.name}" -> fR.write(rangeCondition.v)
        ).asJava
      )
}

case class LessThanKeyCondition[V](val key: Symbol, val v: V) extends DynamoKeyCondition[V] {
  override def keyConditionExpression(s: String): String = s"#$s < :${key.name}"
}

object DynamoKeyCondition {
  object syntax {
    implicit class SymbolKeyCondition(s: Symbol) {
      def ===[V](v: V)(implicit f: DynamoFormat[V]) = EqualsKeyCondition(s, v)

      def <[V](v: V)(implicit f: DynamoFormat[V]) = LessThanKeyCondition(s, v)
    }
  }
}



