package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import simulacrum.typeclass

@typeclass trait UniqueKeyCondition[T] {
  def asAVMap(t: T): Map[String, AttributeValue]
}

object UniqueKeyCondition {
  implicit def uniqueEqualsKey[V: DynamoFormat] = new UniqueKeyCondition[KeyEquals[V]] {
    override def asAVMap(t: KeyEquals[V]): Map[String, AttributeValue] =
      Map(t.key.name -> DynamoFormat[V].write(t.v))
  }
  implicit def uniqueAndEqualsKey[H: UniqueKeyCondition, R: UniqueKeyCondition] = new UniqueKeyCondition[AndEqualsCondition[H, R]] {
    override def asAVMap(t: AndEqualsCondition[H, R]): Map[String, AttributeValue] =
      UniqueKeyCondition[H].asAVMap(t.hashEquality) ++ UniqueKeyCondition[R].asAVMap(t.rangeEquality)
  }
}

case class UniqueKey[T](t: T)(implicit val ukc: UniqueKeyCondition[T]) {
  def asAVMap: Map[String, AttributeValue] = ukc.asAVMap(t)
}