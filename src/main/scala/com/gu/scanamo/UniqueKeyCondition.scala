package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import simulacrum.typeclass

@typeclass trait UniqueKeyCondition[T] {
  def asAVMap(t: T): Map[String, AttributeValue]
}

object UniqueKeyCondition {
  implicit def uniqueEqualsKey[V] = new UniqueKeyCondition[KeyEquals[V]] {
    override def asAVMap(t: KeyEquals[V]): Map[String, AttributeValue] =
      Map(t.key.name -> t.f.write(t.v))
  }
  implicit def uniqueAndEqualsKey[H, R] = new UniqueKeyCondition[AndEqualsCondition[H, R]] {
    override def asAVMap(t: AndEqualsCondition[H, R]): Map[String, AttributeValue] =
      t.hashCondition.asAVMap(t.hashEquality) ++ t.rangeCondition.asAVMap(t.rangeEquality)
  }
}

case class UniqueKey[T](t: T)(implicit val ukc: UniqueKeyCondition[T]) {
  def asAVMap: Map[String, AttributeValue] = ukc.asAVMap(t)
}