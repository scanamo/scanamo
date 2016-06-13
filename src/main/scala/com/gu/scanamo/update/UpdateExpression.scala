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
        "SET #update = :update"
      override def attributeNames(t: SetExpression[V]): Map[String, String] =
        Map("#update" -> t.field.name)
      override def attributeValues(t: SetExpression[V]): Map[String, AttributeValue] =
        Map(":update" -> format.write(t.value))
    }
}

case class AppendExpression[V: DynamoFormat](field: Symbol, value: V)

object AppendExpression {
  implicit def appendUpdateExpression[V](implicit format: DynamoFormat[V]) =
    new UpdateExpression[AppendExpression[V]] {
      override def expression(t: AppendExpression[V]): String =
        "SET #update = list_append(#update, :update)"
      override def attributeNames(t: AppendExpression[V]): Map[String, String] =
        Map("#update" -> t.field.name)
      override def attributeValues(t: AppendExpression[V]): Map[String, AttributeValue] =
        Map(":update" -> DynamoFormat.listFormat[V].write(List(t.value)))
    }
}

case class PrependExpression[V: DynamoFormat](field: Symbol, value: V)

object PrependExpression {
  implicit def appendUpdateExpression[V](implicit format: DynamoFormat[V]) =
    new UpdateExpression[PrependExpression[V]] {
      override def expression(t: PrependExpression[V]): String =
        "SET #update = list_append(:update, #update)"
      override def attributeNames(t: PrependExpression[V]): Map[String, String] =
        Map("#update" -> t.field.name)
      override def attributeValues(t: PrependExpression[V]): Map[String, AttributeValue] =
        Map(":update" -> DynamoFormat.listFormat[V].write(List(t.value)))
    }
}

case class AndUpdate[L: UpdateExpression, R: UpdateExpression](l: L, r: R)

object AndUpdate {
  implicit def andUpdateExpression[L, R](implicit lUpdate: UpdateExpression[L], rUpdate: UpdateExpression[R]) =
    new UpdateExpression[AndUpdate[L, R]] {

      def prefixKeys[T](map: Map[String, T], prefix: String, magicChar: Char) = map.map {
        case (k, v) => (newKey(k, prefix, magicChar), v)
      }
      def newKey(oldKey: String, prefix: String, magicChar: Char) =
        s"$magicChar$prefix${oldKey.stripPrefix(magicChar.toString)}"

      override def expression(t: AndUpdate[L, R]): String = {
        def prefixKeysIn(string: String, keys: Iterable[String], prefix: String, magicChar: Char) =
          keys.foldLeft(string)((result, key) => result.replaceAllLiterally(key, newKey(key, prefix, magicChar)))

        val lPrefixedNamePlaceholders = prefixKeysIn(
          lUpdate.expression(t.l).replace("SET ", ""),
          lUpdate.attributeNames(t.l).keys, "l_", '#')

        val rPrefixedNamePlaceholders = prefixKeysIn(
          rUpdate.expression(t.r).replace("SET ", ""),
          rUpdate.attributeNames(t.r).keys, "r_", '#')

        val lPrefixedValuePlaceholders =
          prefixKeysIn(lPrefixedNamePlaceholders, lUpdate.attributeValues(t.l).keys, "l_", ':')
        val rPrefixedValuePlaceholders =
          prefixKeysIn(rPrefixedNamePlaceholders, rUpdate.attributeValues(t.r).keys, "r_", ':')

        s"SET $lPrefixedValuePlaceholders, $rPrefixedValuePlaceholders"
      }

      override def attributeNames(t: AndUpdate[L, R]): Map[String, String] = {
        prefixKeys(lUpdate.attributeNames(t.l), "l_", '#') ++ prefixKeys(rUpdate.attributeNames(t.r), "r_", '#')
      }

      override def attributeValues(t: AndUpdate[L, R]): Map[String, AttributeValue] = {
        prefixKeys(lUpdate.attributeValues(t.l), "l_", ':') ++ prefixKeys(rUpdate.attributeValues(t.r), "r_", ':')
      }
    }
}