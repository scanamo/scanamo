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
        "SET #updateA = :updateA"
      override def attributeNames(t: SetExpression[V]): Map[String, String] =
        Map("#updateA" -> t.field.name)
      override def attributeValues(t: SetExpression[V]): Map[String, AttributeValue] =
        Map(":updateA" -> format.write(t.value))
    }
}

case class AppendExpression[V: DynamoFormat](field: Symbol, value: V)

object AppendExpression {
  implicit def appendUpdateExpression[V](implicit format: DynamoFormat[V]) =
    new UpdateExpression[AppendExpression[V]] {
      override def expression(t: AppendExpression[V]): String =
        "SET #updateA = list_append(#updateA, :updateA)"
      override def attributeNames(t: AppendExpression[V]): Map[String, String] =
        Map("#updateA" -> t.field.name)
      override def attributeValues(t: AppendExpression[V]): Map[String, AttributeValue] =
        Map(":updateA" -> DynamoFormat.listFormat[V].write(List(t.value)))
    }
}