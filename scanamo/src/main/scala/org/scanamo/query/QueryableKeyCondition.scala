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
        s"#K = :${t.key.placeholder("")}",
        Map("#K" -> t.key.placeholder("")),
        Some(DynamoObject(t.key.placeholder("") -> t.v))
      )
  }

  implicit def hashAndRangeQueryCondition[H: DynamoFormat, R: DynamoFormat] =
    new QueryableKeyCondition[AndQueryCondition[H, R]] {
      final def apply(t: AndQueryCondition[H, R]) =
        RequestCondition(
          s"#K = :${t.hashCondition.key.placeholder("")} AND ${t.rangeCondition.keyConditionExpression("R")}",
          Map("#K" -> t.hashCondition.key.placeholder("")) ++ t.rangeCondition.key.attributeNames("#R"),
          Some(
            DynamoObject(t.hashCondition.key.placeholder("") -> t.hashCondition.v) <> DynamoObject(
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
