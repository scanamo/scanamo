package com.gu.scanamo.update

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.DynamoFormat
import simulacrum.typeclass

@typeclass trait UpdateExpression[T] {
  def expression(t: T): String
  def attributeNames(t: T): Map[String, String]
  def attributeValues(t: T): Map[String, AttributeValue]
}

case class SetExpression[V: DynamoFormat](field: Symbol, value: V)

object SetExpression {
  implicit def setUpdateExpression[V](implicit format: DynamoFormat[V]) =
    new UpdateExpression[SetExpression[V]] {
      override def expression(t: SetExpression[V]): String =
        "SET #a = :a"
      override def attributeNames(t: SetExpression[V]): Map[String, String] =
        Map("#a" -> t.field.name)
      override def attributeValues(t: SetExpression[V]): Map[String, AttributeValue] =
        Map(":a" -> format.write(t.value))
    }
}
