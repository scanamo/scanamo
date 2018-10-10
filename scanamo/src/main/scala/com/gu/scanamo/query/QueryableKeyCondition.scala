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
      req
        .withKeyConditionExpression(s"#K = :${t.key.name}")
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
          .withExpressionAttributeNames(
            (Map("#K" -> t.hashCondition.key.name) ++ t.rangeCondition.key.attributeNames("#R")).asJava
          )
          .withExpressionAttributeValues(
            (
              t.rangeCondition.attributes.map { attrib =>
                s":${attrib._1}" -> DynamoFormat[R].write(attrib._2)
              } + (s":${t.hashCondition.key.name}" -> DynamoFormat[H].write(t.hashCondition.v))
            ).asJava
          )
    }

  implicit def andEqualsKeyCondition[H: UniqueKeyCondition, R: UniqueKeyCondition] =
    new QueryableKeyCondition[AndEqualsCondition[H, R]] {
      override def apply(t: AndEqualsCondition[H, R])(req: QueryRequest): QueryRequest = {
        val m = UniqueKeyCondition[AndEqualsCondition[H, R]].asAVMap(t)
        val charWithKey = m.keySet.toList.zipWithIndex.map {
          case (k, v) => (s"#${('A'.toInt + v).toChar}", k)
        }

        req
          .withKeyConditionExpression(
            charWithKey.map { case (char, key) => s"$char = :$key" }.mkString(" AND ")
          )
          .withExpressionAttributeNames(charWithKey.toMap.asJava)
          .withExpressionAttributeValues(
            (m.map { case (k, v) => s":$k" -> v }).asJava
          )
      }
    }

  implicit def descendingQueryCondition[T](implicit condition: QueryableKeyCondition[T]) =
    new QueryableKeyCondition[Descending[T]] {
      override def apply(t: Descending[T])(req: QueryRequest): QueryRequest =
        condition.apply(t.queryCondition)(req).withScanIndexForward(false)
    }
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply(req: QueryRequest): QueryRequest =
    qkc.apply(t)(req)
}
