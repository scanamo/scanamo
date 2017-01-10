package com.gu.scanamo.update

import cats.kernel.Semigroup
import cats.kernel.instances.MapMonoid
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.DynamoFormat
import simulacrum.typeclass

sealed trait UpdateExpression extends Product with Serializable {
  def expression: String = typeExpressions.map{ case (t, e) => s"${t.op} $e" }.mkString(" ")
  def typeExpressions: Map[UpdateType, String]
  def attributeNames: Map[String, String]
  def attributeValues: Map[String, AttributeValue]
}

object UpdateExpression {
  implicit object Semigroup extends Semigroup[UpdateExpression] {
    override def combine(x: UpdateExpression, y: UpdateExpression): UpdateExpression = AndUpdate(x, y)
  }
}

sealed trait UpdateType { val op: String }
case object SET extends UpdateType { override val op = "SET" }
case object ADD extends UpdateType { override val op = "ADD" }
case object DELETE extends UpdateType { override val op = "DELETE" }
case object REMOVE extends UpdateType { override val op = "REMOVE" }

case class SetExpression[V: DynamoFormat](field: Symbol, value: V) extends UpdateExpression {
  val format = DynamoFormat[V]

  override def typeExpressions: Map[UpdateType, String] =
    Map(SET -> "#update = :update")
  override def attributeNames: Map[String, String] =
    Map("#update" -> field.name)
  override def attributeValues: Map[String, AttributeValue] =
    Map(":update" -> format.write(value))
}

case class AppendExpression[V: DynamoFormat](field: Symbol, value: V) extends UpdateExpression {
  override def typeExpressions: Map[UpdateType, String] =
    Map(SET -> "#update = list_append(if_not_exists(#update, :emptyList), :update)")
  override def attributeNames: Map[String, String] =
    Map("#update" -> field.name)
  override def attributeValues: Map[String, AttributeValue] =
    Map(":update" -> DynamoFormat.listFormat[V].write(List(value)),
        ":emptyList" -> new AttributeValue().withL())
}

case class PrependExpression[V: DynamoFormat](field: Symbol, value: V) extends UpdateExpression {
  override def typeExpressions: Map[UpdateType, String] =
    Map(SET -> "#update = list_append(:update, if_not_exists(#update, :emptyList))")
  override def attributeNames: Map[String, String] =
    Map("#update" -> field.name)
  override def attributeValues: Map[String, AttributeValue] =
    Map(":update" -> DynamoFormat.listFormat[V].write(List(value)),
        ":emptyList" -> new AttributeValue().withL())
}

case class AddExpression[V: DynamoFormat](field: Symbol, value: V) extends UpdateExpression {
  override def typeExpressions: Map[UpdateType, String] =
    Map(ADD -> "#update :update")
  override def attributeNames: Map[String, String] =
    Map("#update" -> field.name)
  override def attributeValues: Map[String, AttributeValue] =
    Map(":update" -> DynamoFormat[V].write(value))
}

/*
Note the difference between DELETE and REMOVE:
 - DELETE is used to delete an element from a set
 - REMOVE is used to remove an attribute from an item
 */

case class DeleteExpression[V: DynamoFormat](field: Symbol, value: V) extends UpdateExpression {
  override def typeExpressions: Map[UpdateType, String] =
    Map(DELETE -> "#update :update")
  override def attributeNames: Map[String, String] =
    Map("#update" -> field.name)
  override def attributeValues: Map[String, AttributeValue] =
    Map(":update" -> DynamoFormat[V].write(value))
}

case class RemoveExpression(field: Symbol) extends UpdateExpression {
  override def typeExpressions: Map[UpdateType, String] =
    Map(REMOVE -> "#update")
  override def attributeNames: Map[String, String] =
    Map("#update" -> field.name)
  override def attributeValues: Map[String, AttributeValue] =
    Map()
}

case class AndUpdate(l: UpdateExpression, r: UpdateExpression) extends UpdateExpression {

  def prefixKeys[T](map: Map[String, T], prefix: String, magicChar: Char) = map.map {
    case (k, v) => (newKey(k, prefix, magicChar), v)
  }
  def newKey(oldKey: String, prefix: String, magicChar: Char) =
    s"$magicChar$prefix${oldKey.stripPrefix(magicChar.toString)}"

  val expressionMonoid = new MapMonoid[UpdateType, String]()(new Semigroup[String] {
    override def combine(x: String, y: String): String = s"$x, $y"
  })

  override def typeExpressions: Map[UpdateType, String] =  {
    def prefixKeysIn(string: String, keys: Iterable[String], prefix: String, magicChar: Char) =
      keys.foldLeft(string)((result, key) => result.replaceAllLiterally(key, newKey(key, prefix, magicChar)))

    val lPrefixedNamePlaceholders = l.typeExpressions.mapValues(exp =>
      prefixKeysIn(exp, l.attributeNames.keys, "l_", '#'))

    val rPrefixedNamePlaceholders = r.typeExpressions.mapValues(exp =>
      prefixKeysIn(exp, r.attributeNames.keys, "r_", '#'))

    val lPrefixedValuePlaceholders = lPrefixedNamePlaceholders.mapValues(exp =>
      prefixKeysIn(exp, l.attributeValues.keys, "l_", ':'))
    val rPrefixedValuePlaceholders = rPrefixedNamePlaceholders.mapValues(exp =>
      prefixKeysIn(exp, r.attributeValues.keys, "r_", ':'))

    expressionMonoid.combine(lPrefixedValuePlaceholders, rPrefixedValuePlaceholders)
  }

  override def attributeNames: Map[String, String] =
    prefixKeys(l.attributeNames, "l_", '#') ++ prefixKeys(r.attributeNames, "r_", '#')

  override def attributeValues: Map[String, AttributeValue] =
    prefixKeys(l.attributeValues, "l_", ':') ++ prefixKeys(r.attributeValues, "r_", ':')
}
