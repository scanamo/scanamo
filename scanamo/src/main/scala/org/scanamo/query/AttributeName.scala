package org.scanamo.query

import org.scanamo.DynamoFormat
import org.scanamo.syntax.Bounds

sealed abstract class AttributeName extends Product with Serializable { self =>

  final def placeholder(prefix: String): String = self match {
    case AttributeName.TopLevel(x)       => prefix ++ x
    case AttributeName.ListElement(a, i) => a.placeholder(prefix) ++ s"[$i]"
    case AttributeName.MapElement(a, x)  => a.placeholder(prefix) ++ ".#" ++ prefix ++ x
  }

  final def attributeNames(prefix: String): Map[String, String] = self match {
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
  final def between[V: DynamoFormat](bounds: Bounds[V]) = Between(self, bounds)
}

object AttributeName {
  def of(s: String): AttributeName = TopLevel(s)

  final case class TopLevel private[AttributeName] (x: String) extends AttributeName
  final case class ListElement private[AttributeName] (a: AttributeName, i: Int) extends AttributeName
  final case class MapElement private[AttributeName] (a: AttributeName, x: String) extends AttributeName
}
