package com.gu.scanamo.update

import cats.kernel.Semigroup
import cats.kernel.std.MapMonoid
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.DynamoFormat
import simulacrum.typeclass

@typeclass trait UpdateExpression[T] {
  def expression(t: T): String = typeExpressions(t).map{ case (t, e) => s"${t.op} $e" }.mkString(" ")
  def typeExpressions(t: T): Map[UpdateType, String]
  def attributeNames(t: T): Map[String, String]
  def attributeValues(t: T): Map[String, AttributeValue]
}

sealed trait UpdateType { val op: String }
final object SET extends UpdateType { override val op = "SET" }
final object ADD extends UpdateType { override val op = "ADD" }

case class SetExpression[V: DynamoFormat](field: Symbol, value: V)

object SetExpression {
  implicit def setUpdateExpression[V](implicit format: DynamoFormat[V]) =
    new UpdateExpression[SetExpression[V]] {
      override def typeExpressions(t: SetExpression[V]): Map[UpdateType, String] =
        Map(SET -> "#update = :update")
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
      override def typeExpressions(t: AppendExpression[V]): Map[UpdateType, String] =
        Map(SET -> "#update = list_append(#update, :update)")
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
      override def typeExpressions(t: PrependExpression[V]): Map[UpdateType, String] =
        Map(SET -> "#update = list_append(:update, #update)")
      override def attributeNames(t: PrependExpression[V]): Map[String, String] =
        Map("#update" -> t.field.name)
      override def attributeValues(t: PrependExpression[V]): Map[String, AttributeValue] =
        Map(":update" -> DynamoFormat.listFormat[V].write(List(t.value)))
    }
}

case class AddExpression[V: DynamoFormat](field: Symbol, value: V)

object AddExpression {
  implicit def addUpdateExpression[V](implicit format: DynamoFormat[V]) =
    new UpdateExpression[AddExpression[V]] {
      override def typeExpressions(t: AddExpression[V]): Map[UpdateType, String] =
        Map(ADD -> "#update :update")
      override def attributeNames(t: AddExpression[V]): Map[String, String] =
        Map("#update" -> t.field.name)
      override def attributeValues(t: AddExpression[V]): Map[String, AttributeValue] =
        Map(":update" -> format.write(t.value))
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

      val expressionMonoid = new MapMonoid[UpdateType, String]()(new Semigroup[String] {
        override def combine(x: String, y: String): String = s"$x, $y"
      })

      override def typeExpressions(t: AndUpdate[L, R]): Map[UpdateType, String] = {
        def prefixKeysIn(string: String, keys: Iterable[String], prefix: String, magicChar: Char) =
          keys.foldLeft(string)((result, key) => result.replaceAllLiterally(key, newKey(key, prefix, magicChar)))

        val lPrefixedNamePlaceholders = lUpdate.typeExpressions(t.l).mapValues(exp =>
          prefixKeysIn(exp, lUpdate.attributeNames(t.l).keys, "l_", '#'))

        val rPrefixedNamePlaceholders = rUpdate.typeExpressions(t.r).mapValues(exp =>
          prefixKeysIn(exp, rUpdate.attributeNames(t.r).keys, "r_", '#'))

        val lPrefixedValuePlaceholders = lPrefixedNamePlaceholders.mapValues(exp =>
          prefixKeysIn(exp, lUpdate.attributeValues(t.l).keys, "l_", ':'))
        val rPrefixedValuePlaceholders = rPrefixedNamePlaceholders.mapValues(exp =>
          prefixKeysIn(exp, rUpdate.attributeValues(t.r).keys, "r_", ':'))

        expressionMonoid.combine(lPrefixedValuePlaceholders, rPrefixedValuePlaceholders)
      }

      override def attributeNames(t: AndUpdate[L, R]): Map[String, String] = {
        prefixKeys(lUpdate.attributeNames(t.l), "l_", '#') ++ prefixKeys(rUpdate.attributeNames(t.r), "r_", '#')
      }

      override def attributeValues(t: AndUpdate[L, R]): Map[String, AttributeValue] = {
        prefixKeys(lUpdate.attributeValues(t.l), "l_", ':') ++ prefixKeys(rUpdate.attributeValues(t.r), "r_", ':')
      }
    }
}