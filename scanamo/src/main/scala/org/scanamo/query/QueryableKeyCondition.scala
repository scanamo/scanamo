package org.scanamo.query

import org.scanamo.{ DynamoFormat, DynamoObject }
import org.scanamo.request.RequestCondition
import simulacrum.typeclass

@typeclass trait QueryableKeyCondition[T] {
  def apply(t: T): RequestCondition
}

object QueryableKeyCondition {
  implicit def equalsKeyCondition[V: DynamoFormat] = new QueryableKeyCondition[KeyEquals[V]] {
    final def apply(t: KeyEquals[V]) =
      RequestCondition(
        s"#K = :${t.key.name}",
        Map("#K" -> t.key.name),
        Some(DynamoObject(t.key.name -> t.v))
      )
  }

  implicit def hashAndRangeQueryCondition[H: DynamoFormat, R: DynamoFormat] =
    new QueryableKeyCondition[AndQueryCondition[H, R]] {
      final def apply(t: AndQueryCondition[H, R]) =
        RequestCondition(
          s"#K = :${t.hashCondition.key.name} AND ${t.rangeCondition.keyConditionExpression("R")}",
          Map("#K" -> t.hashCondition.key.name) ++ t.rangeCondition.key.attributeNames("#R"),
          Some(
            DynamoObject(t.hashCondition.key.name -> t.hashCondition.v) <> DynamoObject(
              t.rangeCondition.attributes.toSeq: _*
            )
          )
        )
    }

  implicit def andEqualsKeyCondition[H: UniqueKeyCondition, R: UniqueKeyCondition](
    implicit HR: UniqueKeyCondition[AndEqualsCondition[H, R]]
  ) =
    new QueryableKeyCondition[AndEqualsCondition[H, R]] {
      final def apply(t: AndEqualsCondition[H, R]) = {
        val m: DynamoObject = HR.toDynamoObject(t)
        val charWithKey: Iterable[(String, String)] = m.keys.zipWithIndex map {
          case (k, v) => (s"#${('A'.toInt + v).toChar}", k)
        }

        RequestCondition(
          charWithKey.map { case (char, key) => s"$char = :$key" }.mkString(" AND "),
          charWithKey.toMap,
          Some(m)
        )
      }
    }
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply: RequestCondition = qkc.apply(t)
}
