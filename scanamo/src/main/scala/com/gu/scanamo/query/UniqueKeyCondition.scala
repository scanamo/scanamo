package org.scanamo.query

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.scanamo.DynamoFormat
import simulacrum.typeclass

@typeclass trait UniqueKeyCondition[T] {
  type K
  def asAVMap(t: T): Map[String, AttributeValue]
  def fromAVMap(key: K, map: Map[String, AttributeValue]): Option[T]
  def key(t: T): K
}

object UniqueKeyCondition {
  implicit def uniqueEqualsKey[V: DynamoFormat] = new UniqueKeyCondition[KeyEquals[V]] {
    type K = Symbol
    override def asAVMap(t: KeyEquals[V]): Map[String, AttributeValue] =
      Map(t.key.name -> DynamoFormat[V].write(t.v))
    override def fromAVMap(key: K, map: Map[String, AttributeValue]) =
      map.get(key.name).flatMap(DynamoFormat[V].read(_).fold(_ => None, v => Some(KeyEquals(key, v))))
    override def key(t: KeyEquals[V]) = t.key
  }
  implicit def uniqueAndEqualsKey[H: UniqueKeyCondition, R: UniqueKeyCondition] =
    new UniqueKeyCondition[AndEqualsCondition[H, R]] {
      val H = UniqueKeyCondition[H]
      val R = UniqueKeyCondition[R]
      type K = (H.K, R.K)
      override def asAVMap(t: AndEqualsCondition[H, R]): Map[String, AttributeValue] =
        H.asAVMap(t.hashEquality) ++ R.asAVMap(t.rangeEquality)
      override def fromAVMap(key: K, map: Map[String, AttributeValue]) =
        for {
          h <- H.fromAVMap(key._1, map)
          r <- R.fromAVMap(key._2, map)
        } yield AndEqualsCondition(h, r)
      override def key(t: AndEqualsCondition[H, R]) = (H.key(t.hashEquality), R.key(t.rangeEquality))
    }
}

case class UniqueKey[T: UniqueKeyCondition](t: T) {
  def asAVMap: Map[String, AttributeValue] = UniqueKeyCondition[T].asAVMap(t)
}

@typeclass trait UniqueKeyConditions[T] {
  def asAVMap(t: T): Set[Map[String, AttributeValue]]
}

object UniqueKeyConditions {
  implicit def keyList[V: DynamoFormat] = new UniqueKeyConditions[KeyList[V]] {
    override def asAVMap(kl: KeyList[V]): Set[Map[String, AttributeValue]] =
      kl.values.map(v => Map(kl.key.name -> DynamoFormat[V].write(v)))
  }
  implicit def multipleKeyList[H: DynamoFormat, R: DynamoFormat] =
    new UniqueKeyConditions[MultipleKeyList[H, R]] {
      override def asAVMap(mkl: MultipleKeyList[H, R]): Set[Map[String, AttributeValue]] = {
        val (hashKey, rangeKey) = mkl.keys
        mkl.values.map {
          case (h, r) =>
            Map(hashKey.name -> DynamoFormat[H].write(h), rangeKey.name -> DynamoFormat[R].write(r))
        }
      }
    }
}

case class UniqueKeys[T: UniqueKeyConditions](t: T) {
  def asAVMap: Set[Map[String, AttributeValue]] = UniqueKeyConditions[T].asAVMap(t)
}

case class KeyList[T: DynamoFormat](key: Symbol, values: Set[T])
case class MultipleKeyList[H: DynamoFormat, R: DynamoFormat](keys: (Symbol, Symbol), values: Set[(H, R)])
