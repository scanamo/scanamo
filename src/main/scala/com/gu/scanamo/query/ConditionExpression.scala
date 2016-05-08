package com.gu.scanamo.query

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.ScanamoRequest._
import com.gu.scanamo.ops.ScanamoOps
import simulacrum.typeclass

import scala.collection.convert.decorateAsJava._

case class Condition[T](tableName: String, t: T)(implicit state: ConditionExpression[T]) {
  def put[V: DynamoFormat](item: V): ScanamoOps[Xor[ConditionalCheckFailedException, PutItemResult]] =
    ScanamoOps.conditionalPut(state.apply(t)(putRequest(tableName)(item)))
}

@typeclass trait ConditionExpression[T] {
  def apply(t: T)(req: PutItemRequest): PutItemRequest
}

object ConditionExpression {
  implicit def symbolValueEqualsCondition[V: DynamoFormat] = new ConditionExpression[(Symbol, V)] {
    override def apply(pair: (Symbol, V))(req: PutItemRequest): PutItemRequest =
      req.withConditionExpression(s"#a = :${pair._1.name}")
        .withExpressionAttributeNames(Map("#a" -> pair._1.name).asJava)
        .withExpressionAttributeValues(Map(s":${pair._1.name}" -> DynamoFormat[V].write(pair._2)).asJava)
  }

  implicit def attributeEqualsCondition[V: DynamoFormat] = new ConditionExpression[KeyEquals[V]] {
    override def apply(t: KeyEquals[V])(req: PutItemRequest): PutItemRequest =
      req.withConditionExpression(s"#a = :${t.key.name}")
        .withExpressionAttributeNames(Map("#a" -> t.key.name).asJava)
        .withExpressionAttributeValues(Map(s":${t.key.name}" -> DynamoFormat[V].write(t.v)).asJava)
  }

  implicit def attributeExistsCondition = new ConditionExpression[AttributeExists] {
    override def apply(t: AttributeExists)(req: PutItemRequest): PutItemRequest =
      req.withConditionExpression(s"attribute_exists(#attr)")
        .withExpressionAttributeNames(Map("#attr" -> t.key.name).asJava)
  }

  implicit def notCondition[T](implicit pcs: ConditionExpression[T]) = new ConditionExpression[Not[T]] {
    override def apply(not: Not[T])(req: PutItemRequest): PutItemRequest = {
      val requestToNegate = pcs(not.condition)(req)
      req.withConditionExpression(s"NOT(${requestToNegate.getConditionExpression})")
    }
  }
}