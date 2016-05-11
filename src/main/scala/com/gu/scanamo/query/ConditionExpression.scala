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

  implicit def beginsWithCondition[V: DynamoFormat] = new ConditionExpression[BeginsWith[V]] {
    override def apply(b: BeginsWith[V])(req: ScanamoPutRequest): ScanamoPutRequest =
      req.copy(condition = Some(
        RequestCondition(
          s"begins_with(#a, :${b.key.name})",
          Map("#a" -> b.key.name),
          Some(Map(s":${b.key.name}" -> DynamoFormat[V].write(b.v)))
        )
      ))
  }

  implicit def keyIsCondition[V: DynamoFormat] = new ConditionExpression[KeyIs[V]] {
    override def apply(k: KeyIs[V])(req: ScanamoPutRequest): ScanamoPutRequest = {
      req.copy(condition = Some(
        RequestCondition(
          s"#a ${k.operator.op} :${k.key.name}",
          Map("#a" -> k.key.name),
          Some(Map(s":${k.key.name}" -> DynamoFormat[V].write(k.v)))
        )
      ))
    }
  }

  implicit def andCondition[L, R](implicit lce: ConditionExpression[L], rce: ConditionExpression[R]) =
    new ConditionExpression[AndCondition[L, R]] {
      override def apply(and: AndCondition[L, R])(req: ScanamoPutRequest): ScanamoPutRequest = {

        val condition = combineConditions(req, and.l, and.r, "AND")
        req.copy(condition = condition)
      }
    }

  implicit def orCondition[L, R](implicit lce: ConditionExpression[L], rce: ConditionExpression[R]) =
    new ConditionExpression[OrCondition[L, R]] {
      override def apply(and: OrCondition[L, R])(req: ScanamoPutRequest): ScanamoPutRequest = {

        val condition = combineConditions(req, and.l, and.r, "OR")
        req.copy(condition = condition)
      }
    }

  private def combineConditions[L, R](req: ScanamoPutRequest, l: L, r: R, combininingOperator: String)(
    implicit lce: ConditionExpression[L], rce: ConditionExpression[R]): Option[RequestCondition] = {
    def prefixKeys[T](map: Map[String, T], prefix: String, magicChar: Char) = map.map {
      case (k, v) => (newKey(k, prefix, magicChar), v)
    }
    def newKey(oldKey: String, prefix: String, magicChar: Char) =
      s"$magicChar$prefix${oldKey.stripPrefix(magicChar.toString)}"

    def prefixKeysIn(string: String, keys: Iterable[String], prefix: String, magicChar: Char) =
      keys.foldLeft(string)((result, key) => result.replaceAllLiterally(key, newKey(key, prefix, magicChar)))

    val lPrefix = s"${combininingOperator.toLowerCase}_l_"
    val rPrefix = s"${combininingOperator.toLowerCase}_r_"

    for {
      lCondition <- lce(l)(req).condition
      rCondition <- rce(r)(req).condition
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
        s"($lConditionExpression $combininingOperator $rConditionExpression)",
        mergedExpressionAttributeNames,
        mergedExpressionAttributeValues)
    }
  }
}

case class AndCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class OrCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class Condition[T: ConditionExpression](t: T) {
  def and[Y: ConditionExpression](other: Y) = AndCondition(t, other)
  def or[Y: ConditionExpression](other: Y) = OrCondition(t, other)
}