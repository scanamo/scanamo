package com.gu.scanamo.query

import com.amazonaws.services.dynamodbv2.model.QueryRequest
import com.gu.scanamo.DynamoFormat
import simulacrum.typeclass

import scala.collection.convert.decorateAsJava._

@typeclass trait QueryableKeyCondition[T] {
  def apply(t: T)(req: QueryRequest): QueryRequest
}

object QueryableKeyCondition {
  implicit def equalsKeyCondition[V: DynamoFormat] = new QueryableKeyCondition[HashKeyEquals[V]] {
    override def apply(t: HashKeyEquals[V])(req: QueryRequest): QueryRequest =
      req.withKeyConditionExpression(s"#K = :${t.key.name}")
        .withExpressionAttributeNames(Map("#K" -> t.key.name).asJava)
        .withExpressionAttributeValues(Map(s":${t.key.name}" -> DynamoFormat[V].write(t.v)).asJava)
        .withScanIndexForward(t.order match  {
          case Ascending => true
          case Descending => false
        })
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
          .withScanIndexForward(t.order match  {
            case Ascending => true
            case Descending => false
          })
    }
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply(req: QueryRequest): QueryRequest =
    qkc.apply(t)(req)
}
