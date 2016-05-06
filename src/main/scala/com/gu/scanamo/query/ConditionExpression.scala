package com.gu.scanamo.query

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.ScanamoRequest._
import com.gu.scanamo.ops.ScanamoOps
import simulacrum.typeclass

import scala.collection.convert.decorateAsJava._

case class Condition[T](tableName: String, t: T)(implicit state: PutConditionState[T]) {
  def put[V: DynamoFormat](item: V): ScanamoOps[Xor[ConditionalCheckFailedException, PutItemResult]] =
    ScanamoOps.conditionalPut(state.apply(t)(putRequest(tableName)(item)))
}

@typeclass trait PutConditionState[T] {
  def apply(t: T)(req: PutItemRequest): PutItemRequest
}

object PutConditionState {
  implicit def attributeEqualsCondition[V: DynamoFormat] = new PutConditionState[KeyEquals[V]] {
    override def apply(t: KeyEquals[V])(req: PutItemRequest): PutItemRequest =
      req.withConditionExpression(s"#a = :${t.key.name}")
        .withExpressionAttributeNames(Map("#a" -> t.key.name).asJava)
        .withExpressionAttributeValues(Map(s":${t.key.name}" -> DynamoFormat[V].write(t.v)).asJava)
  }

  implicit def attributeExistsCondition = new PutConditionState[AttributeExists] {
    override def apply(t: AttributeExists)(req: PutItemRequest): PutItemRequest =
      req.withConditionExpression(s"attribute_exists(#attr)")
        .withExpressionAttributeNames(Map("#attr" -> t.key.name).asJava)
  }
}

case class ConditionExpression[T](t: T)(implicit pc: PutConditionState[T]) {
  def apply(req: PutItemRequest): PutItemRequest =
    pc.apply(t)(req)
}
