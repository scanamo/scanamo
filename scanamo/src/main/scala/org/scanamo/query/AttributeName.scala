package org.scanamo.query

import org.scanamo.DynamoFormat
import org.scanamo.syntax.Bounds

case class AttributeName(components: List[String], index: Option[Int]) {
  def placeholder(prefix: String): String =
    index.foldLeft(
      components.map(s => s"$prefix$s").mkString(".#")
    )(
      (p, i) => s"$p[$i]"
    )

  def attributeNames(prefix: String): Map[String, String] =
    Map(components.map(s => s"$prefix$s" -> s): _*)

  def \(component: String) = copy(components = components :+ component)

  def apply(index: Int): AttributeName = copy(index = Some(index))

  def <[V: DynamoFormat](v: V) = KeyIs(this, LT, v)
  def >[V: DynamoFormat](v: V) = KeyIs(this, GT, v)
  def <=[V: DynamoFormat](v: V) = KeyIs(this, LTE, v)
  def >=[V: DynamoFormat](v: V) = KeyIs(this, GTE, v)
  def beginsWith[V: DynamoFormat](v: V) = BeginsWith(this, v)
  def between[V: DynamoFormat](bounds: Bounds[V]) = Between(this, bounds)

  override def toString(): String = index.foldLeft(components.mkString("."))((x, y) => x ++ "[" ++ y.toString ++ "]")
}

object AttributeName {
  def of(s: String): AttributeName = AttributeName(List(s), None)
}
