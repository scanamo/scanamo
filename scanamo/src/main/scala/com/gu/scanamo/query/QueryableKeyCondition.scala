package org.scanamo.query

import org.scanamo.DynamoFormat
import simulacrum.typeclass
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

import collection.JavaConverters._

@typeclass trait QueryableKeyCondition[T] {
  def apply(t: T)(req: QueryRequest.Builder): QueryRequest.Builder
}

object QueryableKeyCondition {
  implicit def equalsKeyCondition[V: DynamoFormat] = new QueryableKeyCondition[KeyEquals[V]] {
    override def apply(t: KeyEquals[V])(req: QueryRequest.Builder): QueryRequest.Builder =
      req.keyConditionExpression(s"#K = :${t.key.name}")
        .expressionAttributeNames(Map("#K" -> t.key.name).asJava)
        .expressionAttributeValues(Map(s":${t.key.name}" -> DynamoFormat[V].write(t.v)).asJava)
  }

  implicit def hashAndRangeQueryCondition[H: DynamoFormat, R: DynamoFormat] =
    new QueryableKeyCondition[AndQueryCondition[H, R]] {
      override def apply(t: AndQueryCondition[H, R])(req: QueryRequest.Builder): QueryRequest.Builder =
        req
          .keyConditionExpression(
            s"#K = :${t.hashCondition.key.name} AND ${t.rangeCondition.keyConditionExpression("R")}"
          )
          .expressionAttributeNames(
            (Map("#K" -> t.hashCondition.key.name) ++ t.rangeCondition.key.attributeNames("#R")).asJava
          )
          .expressionAttributeValues(
            (
              t.rangeCondition.attributes.map { attrib =>
                s":${attrib._1}" -> DynamoFormat[R].write(attrib._2)
              } + (s":${t.hashCondition.key.name}" -> DynamoFormat[H].write(t.hashCondition.v))
            ).asJava
          )
    }

  implicit def andEqualsKeyCondition[H: UniqueKeyCondition, R: UniqueKeyCondition] =
    new QueryableKeyCondition[AndEqualsCondition[H, R]] {
      override def apply(t: AndEqualsCondition[H, R])(req: QueryRequest.Builder): QueryRequest.Builder = {
        val m = UniqueKeyCondition[AndEqualsCondition[H, R]].asAVMap(t)
        val charWithKey = m.keySet.toList.zipWithIndex.map {
          case (k, v) => (s"#${('A'.toInt + v).toChar}", k)
        }

        req
          .keyConditionExpression(
            charWithKey.map { case (char, key) => s"$char = :$key" }.mkString(" AND ")
          )
          .expressionAttributeNames(charWithKey.toMap.asJava)
          .expressionAttributeValues(
            m.map { case (k, v) => s":$k" -> v }.asJava
          )
      }
    }

  implicit def descendingQueryCondition[T](implicit condition: QueryableKeyCondition[T]) =
    new QueryableKeyCondition[Descending[T]] {
      override def apply(t: Descending[T])(req: QueryRequest.Builder): QueryRequest.Builder =
        condition.apply(t.queryCondition)(req).scanIndexForward(false)
    }
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply(req: QueryRequest.Builder): QueryRequest =
    qkc.apply(t)(req).build()
}
