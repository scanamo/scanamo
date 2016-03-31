package com.gu.scanamo

import java.util

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

case class UniqueKey[T: UniqueKeyCondition](t: T) {
  def asAVMap: Map[String, AttributeValue] = UniqueKeyCondition[T].asAVMap(t)
}

@typeclass trait UniqueKeyConditions[T] {
  def asAVMap(t: T): List[Map[String, AttributeValue]]
  def sortByKeys(t: T, l: List[java.util.Map[String, AttributeValue]]): List[java.util.Map[String, AttributeValue]]
}

object UniqueKeyConditions {
  implicit def keyList[V: DynamoFormat] = new UniqueKeyConditions[KeyList[V]] {
    override def asAVMap(kl: KeyList[V]): List[Map[String, AttributeValue]] =
      kl.values.map(v => Map(kl.key.name -> DynamoFormat[V].write(v)))

    override def sortByKeys(keyList: KeyList[V], l: List[java.util.Map[String, AttributeValue]]): List[java.util.Map[String, AttributeValue]] = {
      val keyValueOptions = keyList.values.map(Option(_))
      def keyValueOption(avMap: java.util.Map[String, AttributeValue]): Option[V] =
        DynamoFormat[V].read(avMap.get(keyList.key.name)).toOption
      l.sortBy(i => keyValueOptions.indexOf(keyValueOption(i)))
    }


  }
  implicit def multipleKeyList[H: DynamoFormat, R: DynamoFormat] =
    new UniqueKeyConditions[MultipleKeyList[H, R]] {
      override def asAVMap(mkl: MultipleKeyList[H, R]): List[Map[String, AttributeValue]] = {
        val (hashKey, rangeKey) = mkl.keys
        mkl.values.map { case (h, r) =>
          Map(hashKey.name -> DynamoFormat[H].write(h), rangeKey.name -> DynamoFormat[R].write(r))
        }
      }

      override def sortByKeys(mkl: MultipleKeyList[H, R], l: List[util.Map[String, AttributeValue]]): List[util.Map[String, AttributeValue]] = {
        val (hash, range) = mkl.keys
        val keyValueOptions = mkl.values.map(Option(_))
        def keyValueOption(avMap: java.util.Map[String, AttributeValue]): Option[(H, R)] =
          for {
            h <- DynamoFormat[H].read(avMap.get(hash.name)).toOption
            r <- DynamoFormat[R].read(avMap.get(range.name)).toOption
          } yield (h, r)

        l.sortBy(i => keyValueOptions.indexOf(keyValueOption(i)))
      }
    }
}

case class UniqueKeys[T: UniqueKeyConditions](t: T) {
  def asAVMap: List[Map[String, AttributeValue]] = UniqueKeyConditions[T].asAVMap(t)
  def sortByKeys(l: List[util.Map[String, AttributeValue]]) = UniqueKeyConditions[T].sortByKeys(t, l)
}

case class KeyList[T: DynamoFormat](key: Symbol, values: List[T])
case class MultipleKeyList[H: DynamoFormat, R: DynamoFormat](keys: (Symbol, Symbol), values: List[(H, R)])