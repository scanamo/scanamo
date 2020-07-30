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

import org.scanamo.DynamoFormat

sealed abstract class AttributeName extends Product with Serializable { self =>
  import AttributeName.PartiallyAppliedBetween

  final def placeholder(prefix: String): String =
    self match {
      case AttributeName.TopLevel(x)       => prefix ++ x
      case AttributeName.ListElement(a, i) => a.placeholder(prefix) ++ s"[$i]"
      case AttributeName.MapElement(a, x)  => a.placeholder(prefix) ++ ".#" ++ prefix ++ x
    }

  final def attributeNames(prefix: String): Map[String, String] =
    self match {
      case AttributeName.TopLevel(x)       => Map((prefix ++ x) -> x)
      case AttributeName.ListElement(a, _) => a.attributeNames(prefix)
      case AttributeName.MapElement(a, x)  => a.attributeNames(prefix) ++ Map((prefix ++ x) -> x)
    }

  final def \(component: String) = AttributeName.MapElement(self, component)

  final def !(index: Int) = AttributeName.ListElement(self, index)

  final def <[V: DynamoFormat](v: V) = KeyIs(self, LT, v)
  final def >[V: DynamoFormat](v: V) = KeyIs(self, GT, v)
  final def <=[V: DynamoFormat](v: V) = KeyIs(self, LTE, v)
  final def >=[V: DynamoFormat](v: V) = KeyIs(self, GTE, v)
  final def beginsWith[V: DynamoFormat](v: V) = BeginsWith(self, v)
  def between[V: DynamoFormat](lo: V): PartiallyAppliedBetween[V] =
    new PartiallyAppliedBetween(this, lo)
}

object AttributeName {
  def of(s: String): AttributeName = TopLevel(s)

  final case class TopLevel private[AttributeName] (x: String) extends AttributeName
  final case class ListElement private[AttributeName] (a: AttributeName, i: Int) extends AttributeName
  final case class MapElement private[AttributeName] (a: AttributeName, x: String) extends AttributeName

  final private[scanamo] class PartiallyAppliedBetween[V: DynamoFormat](attr: AttributeName, lo: V) {
    def and(hi: V): Between[V] = Between(attr, lo, hi)
  }
}
