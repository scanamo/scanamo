package com.gu.scanamo.query

import com.amazonaws.services.dynamodbv2.model.QueryRequest
import com.gu.scanamo.DynamoFormat
import simulacrum.typeclass

import collection.JavaConverters._

@typeclass trait QueryableKeyCondition[T] {
  def apply(t: T)(req: QueryRequest): QueryRequest
}

object QueryableKeyCondition {
  implicit def equalsKeyCondition[V: DynamoFormat] = new QueryableKeyCondition[KeyEquals[V]] {
    override def apply(t: KeyEquals[V])(req: QueryRequest): QueryRequest =
      req.withKeyConditionExpression(s"#K = :${t.key.name}")
      .addExpressionAttributeNamesEntry("#K", t.key.name)
      .addExpressionAttributeValuesEntry(s":${t.key.name}", DynamoFormat[V].write(t.v))
  }

  implicit def hashAndRangeQueryCondition[H: DynamoFormat, R: DynamoFormat] =
    new QueryableKeyCondition[AndQueryCondition[H, R]] {
      override def apply(t: AndQueryCondition[H, R])(req: QueryRequest): QueryRequest =
        req
        .withKeyConditionExpression(
            s"#K = :${t.hashCondition.key.name} AND ${t.rangeCondition.keyConditionExpression("R")}"
          )
        .addExpressionAttributeNamesEntry("#K", t.hashCondition.key.name)
        .addExpressionAttributeNamesEntry("#R", t.rangeCondition.key.name)
        .addExpressionAttributeValuesEntry(s":${t.hashCondition.key.name}", DynamoFormat[H].write(t.hashCondition.v))
        .addExpressionAttributeValuesEntry(s":${t.rangeCondition.key.name}", DynamoFormat[R].write(t.rangeCondition.v))
    }

  implicit def descendingQueryCondition[T](implicit condition: QueryableKeyCondition[T]) = new QueryableKeyCondition[Descending[T]] {
    override def apply(t: Descending[T])(req: QueryRequest): QueryRequest =
      condition.apply(t.queryCondition)(req).withScanIndexForward(false)
  }
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply(req: QueryRequest): QueryRequest =
    qkc.apply(t)(req)
}
