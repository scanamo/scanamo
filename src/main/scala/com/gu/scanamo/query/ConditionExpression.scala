package com.gu.scanamo.query

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.ScanamoRequest._
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.request.{RequestCondition, ScanamoPutRequest}
import simulacrum.typeclass

case class ConditionalOperation[T](tableName: String, t: T)(implicit state: ConditionExpression[T]) {
  def put[V: DynamoFormat](item: V): ScanamoOps[Xor[ConditionalCheckFailedException, PutItemResult]] =
    ScanamoOps.conditionalPut(state.apply(t)(putRequest(tableName)(item)))
}

@typeclass trait ConditionExpression[T] {
  def apply(t: T)(req: ScanamoPutRequest): ScanamoPutRequest
}

object ConditionExpression {
  implicit def symbolValueEqualsCondition[V: DynamoFormat] = new ConditionExpression[(Symbol, V)] {
    override def apply(pair: (Symbol, V))(req: ScanamoPutRequest): ScanamoPutRequest =
      req.copy(condition = Some(
        RequestCondition(
          s"#a = :${pair._1.name}",
          Map("#a" -> pair._1.name),
          Some(Map(s":${pair._1.name}" -> DynamoFormat[V].write(pair._2)))
        )
      ))
  }

  implicit def attributeExistsCondition = new ConditionExpression[AttributeExists] {
    override def apply(t: AttributeExists)(req: ScanamoPutRequest): ScanamoPutRequest =
      req.copy(condition = Some(
        RequestCondition("attribute_exists(#attr)", Map("#attr" -> t.key.name), None)))
  }

  implicit def notCondition[T](implicit pcs: ConditionExpression[T]) = new ConditionExpression[Not[T]] {
    override def apply(not: Not[T])(req: ScanamoPutRequest): ScanamoPutRequest = {
      val conditionedRequest = pcs(not.condition)(req)
      conditionedRequest.copy(condition = conditionedRequest.condition.map(c => c.copy(s"NOT(${c.expression})")))
    }
  }

  implicit def andCondition[X, Y](implicit lce: ConditionExpression[X], rce: ConditionExpression[Y]) =
    new ConditionExpression[AndCondition[X, Y]] {
      private def prefixKeys[T](map: Map[String, T], prefix: String, magicChar: Char) = map.map {
        case (k, v) => (newKey(k, prefix, magicChar), v)
      }
      private def newKey(oldKey: String, prefix: String, magicChar: Char) =
        s"$magicChar$prefix${oldKey.stripPrefix(magicChar.toString)}"

      private def prefixKeysIn(string: String, keys: Iterable[String], prefix: String, magicChar: Char) =
        keys.foldLeft(string)((result, key) => result.replaceAllLiterally(key, newKey(key, prefix, magicChar)))

      val lPrefix = "and_l_"
      val rPrefix = "and_r_"

      override def apply(and: AndCondition[X, Y])(req: ScanamoPutRequest): ScanamoPutRequest = {

        val condition = for {
          lCondition <- lce(and.l)(req).condition
          rCondition <- rce(and.r)(req).condition
        } yield {
          val mergedExpressionAttributeNames =
            prefixKeys(lCondition.attributeNames, lPrefix, '#') ++
            prefixKeys(rCondition.attributeNames, rPrefix, '#')

          val lValues = lCondition.attributeValues.map(prefixKeys(_, lPrefix, ':'))
          val rValues = rCondition.attributeValues.map(prefixKeys(_, rPrefix, ':'))

          val mergedExpressionAttributeValues = lValues match {
            case Some(m) => Some(m ++ rValues.getOrElse(Map.empty))
            case _ => rValues
          }

          val lConditionExpression =
            prefixKeysIn(
              prefixKeysIn(lCondition.expression, lCondition.attributeNames.keys, lPrefix, '#'),
              lCondition.attributeValues.toList.flatMap(_.keys), lPrefix, ':'
            )
          val rConditionExpression =
            prefixKeysIn(
              prefixKeysIn(rCondition.expression, rCondition.attributeNames.keys, rPrefix, '#'),
              rCondition.attributeValues.toList.flatMap(_.keys), rPrefix, ':'
            )
          RequestCondition(
            s"($lConditionExpression AND $rConditionExpression)",
            mergedExpressionAttributeNames,
            mergedExpressionAttributeValues)
        }

        req.copy(condition = condition)
      }
    }
}

case class AndCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class Condition[T: ConditionExpression](t: T) {
  def and[Y: ConditionExpression](other: Y) = AndCondition(t, other)
}