/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo.query

import org.scanamo.request.{ AttributeNamesAndValues, RequestCondition }
import org.scanamo.{ DynamoFormat, DynamoObject }

trait QueryableKeyCondition[T] {
  def apply(t: T): RequestCondition
}

object QueryableKeyCondition {
  def apply[T](implicit Q: QueryableKeyCondition[T]): QueryableKeyCondition[T] = Q

  implicit def equalsKeyCondition[V: DynamoFormat]: QueryableKeyCondition[KeyEquals[V]] = (t: KeyEquals[V]) =>
    RequestCondition(
      s"#K = :${t.key.placeholder("")}",
      AttributeNamesAndValues(Map("#K" -> t.key.placeholder("")), DynamoObject(t.key.placeholder("") -> t.v))
    )

  implicit def hashAndRangeQueryCondition[H: DynamoFormat, R: DynamoFormat]
    : QueryableKeyCondition[AndQueryCondition[H, R]] = { (t: AndQueryCondition[H, R]) =>
    val tKey = t.hashCondition.key.placeholder("")
    RequestCondition(
      s"#K = :$tKey AND ${t.rangeCondition.keyConditionExpression("R")}",
      AttributeNamesAndValues(
        Map("#K" -> tKey) ++ t.rangeCondition.key.attributeNames("#R"),
        DynamoObject(tKey -> t.hashCondition.v) <> DynamoObject(t.rangeCondition.attributes.toSeq: _*)
      )
    )
  }

  implicit def andEqualsKeyCondition[H: UniqueKeyCondition, R: UniqueKeyCondition](implicit
    HR: UniqueKeyCondition[AndEqualsCondition[H, R]]
  ): QueryableKeyCondition[AndEqualsCondition[H, R]] = (t: AndEqualsCondition[H, R]) => {
    val m: DynamoObject = HR.toDynamoObject(t)
    val charWithKey: Iterable[(String, String)] = m.keys.zipWithIndex map { case (k, v) =>
      (s"#${('A'.toInt + v).toChar}", k)
    }

    RequestCondition(
      charWithKey.map { case (char, key) => s"$char = :$key" }.mkString(" AND "),
      AttributeNamesAndValues(charWithKey.toMap, m)
    )
  }
}

case class Query[T](t: T)(implicit qkc: QueryableKeyCondition[T]) {
  def apply: RequestCondition = qkc.apply(t)
}
