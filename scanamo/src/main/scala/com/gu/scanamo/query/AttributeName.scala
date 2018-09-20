package com.gu.scanamo.query

import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.syntax.Bounds

private[scanamo] case class AttributeName(components: List[Symbol], index: Option[Int]) {
  def placeholder(prefix: String): String =
    index.foldLeft(
      components.map(s => s"$prefix${s.name}").mkString(".#")
    )(
      (p, i) => s"$p[$i]"
    )

  def attributeNames(prefix: String): Map[String, String] =
    Map(components.map(s => s"$prefix${s.name}" -> s.name): _*)

  def \(component: Symbol) = copy(components = components :+ component)

  def apply(index: Int): AttributeName = copy(index = Some(index))

  def <[V: DynamoFormat](v: V) = KeyIs(this, LT, v)
  def >[V: DynamoFormat](v: V) = KeyIs(this, GT, v)
  def <=[V: DynamoFormat](v: V) = KeyIs(this, LTE, v)
  def >=[V: DynamoFormat](v: V) = KeyIs(this, GTE, v)
  def beginsWith[V: DynamoFormat](v: V) = BeginsWith(this, v)
  def between[V: DynamoFormat](bounds: Bounds[V]) = Between(this, bounds)
}

object AttributeName {
  def of(s: Symbol): AttributeName = AttributeName(List(s), None)
}
