package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.QueryRequest
import simulacrum.typeclass

import collection.convert.decorateAsJava._

@typeclass trait QueryableKeyCondition[T] {
  def apply(t: T)(req: QueryRequest): QueryRequest
}

object QueryableKeyCondition {
  implicit def equalsKeyCondition[V] = new QueryableKeyCondition[KeyEquals[V]] {
    override def apply(t: KeyEquals[V])(req: QueryRequest): QueryRequest =
      req.withKeyConditionExpression(s"#K = :${t.key.name}")
        .withExpressionAttributeNames(Map("#K" -> t.key.name).asJava)
        .withExpressionAttributeValues(Map(s":${t.key.name}" -> t.f.write(t.v)).asJava)
  }
  implicit def hashAndRangeQueryCondition[H, R] = new QueryableKeyCondition[AndQueryCondition[H, R]] {
    override def apply(t: AndQueryCondition[H, R])(req: QueryRequest): QueryRequest =
      req
        .withKeyConditionExpression(
          s"#K = :${t.hashCondition.key.name} AND ${t.rangeCondition.keyConditionExpression("R")}"
        )
        .withExpressionAttributeNames(Map("#K" -> t.hashCondition.key.name, "#R" -> t.rangeCondition.key.name).asJava)
        .withExpressionAttributeValues(
          Map(
            s":${t.hashCondition.key.name}" -> t.fH.write(t.hashCondition.v),
            s":${t.rangeCondition.key.name}" -> t.fR.write(t.rangeCondition.v)
          ).asJava
        )
  }
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply(req: QueryRequest): QueryRequest =
    qkc.apply(t)(req)
}
