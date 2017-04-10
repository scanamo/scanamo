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
        .withExpressionAttributeNames(Map("#K" -> t.key.name).asJava)
        .withExpressionAttributeValues(Map(s":${t.key.name}" -> DynamoFormat[V].write(t.v)).asJava)
  }

  implicit def hashAndRangeQueryCondition[H: DynamoFormat, R: DynamoFormat] =
    new QueryableKeyCondition[AndQueryCondition[H, R]] {
      override def apply(t: AndQueryCondition[H, R])(req: QueryRequest): QueryRequest =
        req
          .withKeyConditionExpression(
            s"#K = :${t.hashCondition.key.name} AND ${t.rangeCondition.keyConditionExpression("R")}"
          )
          .withExpressionAttributeNames(Map("#K" -> t.hashCondition.key.name, "#R" -> t.rangeCondition.key.name).asJava)
          .withExpressionAttributeValues(
            Map(
              s":${t.hashCondition.key.name}" -> DynamoFormat[H].write(t.hashCondition.v),
              s":${t.rangeCondition.key.name}" -> DynamoFormat[R].write(t.rangeCondition.v)
            ).asJava
          )
    }

  implicit def descendingQueryCondition[T](implicit condition: QueryableKeyCondition[T]) = new QueryableKeyCondition[Descending[T]] {
    override def apply(t: Descending[T])(req: QueryRequest): QueryRequest =
      condition.apply(t.queryCondition)(req).withScanIndexForward(false)
  }

  implicit def hashAndFilterCondition[H: DynamoFormat, R: DynamoFormat, F: DynamoFormat] =
    new QueryableKeyCondition[AndFilterCondition[H, R, F]] {
      override def apply(t: AndFilterCondition[H, R, F])(req: QueryRequest): QueryRequest = {
        req
          .withKeyConditionExpression(
              s"#K = :${t.hashCondition.key.name} AND ${t.rangeCondition.keyConditionExpression("R")}"
            )
         .addExpressionAttributeNamesEntry("#K", t.hashCondition.key.name)
         .addExpressionAttributeNamesEntry("#R", t.rangeCondition.key.name)
         .addExpressionAttributeValuesEntry(s":${t.hashCondition.key.name}", DynamoFormat[H].write(t.hashCondition.v))
         .addExpressionAttributeValuesEntry(s":${t.rangeCondition.key.name}", DynamoFormat[R].write(t.rangeCondition.v))
         .addExpressionAttributeValuesEntry(s":${t.fieldCondition.key.name}", DynamoFormat[F].write(t.fieldCondition.v))
         .withFilterExpression(s"${t.fieldCondition.keyConditionExpression("F")}")
      }
    }
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply(req: QueryRequest): QueryRequest =
    qkc.apply(t)(req)
}
