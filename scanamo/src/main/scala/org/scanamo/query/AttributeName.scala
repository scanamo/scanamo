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

case class AttributeName(components: List[String], index: Option[Int]) {
  import AttributeName.PartiallyAppliedBetween

  def placeholder(prefix: String): String =
    index.foldLeft(
      components.map(s => s"$prefix$s").mkString(".#")
    )((p, i) => s"$p[$i]")

  def attributeNames(prefix: String): Map[String, String] =
    Map(components.map(s => {
      val alphaNumericS = s.replaceAll("[^A-Za-z0-9]", "")
      s"$prefix$alphaNumericS" -> s
    }): _*)

  def \(component: String) = copy(components = components :+ component)

  def apply(index: Int): AttributeName = copy(index = Some(index))

  def ===[V: DynamoFormat](v: V): KeyEquals[V] = KeyEquals(this, v)
  def <[V: DynamoFormat](v: V): KeyIs[V] = KeyIs(this, LT, v)
  def >[V: DynamoFormat](v: V): KeyIs[V] = KeyIs(this, GT, v)
  def <=[V: DynamoFormat](v: V): KeyIs[V] = KeyIs(this, LTE, v)
  def >=[V: DynamoFormat](v: V): KeyIs[V] = KeyIs(this, GTE, v)
  def beginsWith[V: DynamoFormat](v: V): BeginsWith[V] = BeginsWith(this, v)
  def between[V: DynamoFormat](lo: V): PartiallyAppliedBetween[V] =
    new PartiallyAppliedBetween(this, lo)
  def contains(substr: String): Contains = Contains(this, substr)
  def in[V: DynamoFormat](vs: Set[V]): KeyList[V] = KeyList(this, vs)
}

object AttributeName {
  def of(s: String): AttributeName = AttributeName(List(s), None)

  final private[scanamo] class PartiallyAppliedBetween[V: DynamoFormat](attr: AttributeName, lo: V) {
    def and(hi: V): Between[V] = Between(attr, lo, hi)
  }
}
