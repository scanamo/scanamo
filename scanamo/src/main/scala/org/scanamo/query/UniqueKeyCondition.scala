package org.scanamo.query

import cats.instances.option._
import cats.syntax.apply._
import org.scanamo.{ DynamoFormat, DynamoObject }

trait UniqueKeyCondition[T] {
  type K
  def toDynamoObject(t: T): DynamoObject
  def fromDynamoObject(key: K, dvs: DynamoObject): Option[T]
  def key(t: T): K
}

object UniqueKeyCondition {
  type Aux[T, K0] = UniqueKeyCondition[T] { type K = K0 }

  def apply[T, K](implicit U: UniqueKeyCondition.Aux[T, K]): UniqueKeyCondition.Aux[T, K] = U

  implicit def uniqueEqualsKey[V](implicit V: DynamoFormat[V]) = new UniqueKeyCondition[KeyEquals[V]] {
    type K = AttributeName
    final def toDynamoObject(t: KeyEquals[V]): DynamoObject = DynamoObject(t.key.placeholder("") -> t.v)
    final def fromDynamoObject(key: K, dvs: DynamoObject): Option[KeyEquals[V]] =
      dvs(key.placeholder("")).flatMap(V.read(_).right.toOption.map(KeyEquals(key, _)))
    final def key(t: KeyEquals[V]): K = t.key
  }

  implicit def uniqueAndEqualsKey[H: UniqueKeyCondition, R: UniqueKeyCondition, KH, KR](
    implicit H: UniqueKeyCondition.Aux[H, KH],
    R: UniqueKeyCondition.Aux[R, KR]
  ) =
    new UniqueKeyCondition[AndEqualsCondition[H, R]] {
      type K = (KH, KR)
      final def toDynamoObject(t: AndEqualsCondition[H, R]): DynamoObject =
        H.toDynamoObject(t.hashEquality) <> R.toDynamoObject(t.rangeEquality)
      final def fromDynamoObject(key: K, dvs: DynamoObject): Option[AndEqualsCondition[H, R]] =
        (H.fromDynamoObject(key._1, dvs), R.fromDynamoObject(key._2, dvs)).mapN(AndEqualsCondition(_, _))
      final def key(t: AndEqualsCondition[H, R]): K = (H.key(t.hashEquality), R.key(t.rangeEquality))
    }
}

case class UniqueKey[T](t: T)(implicit T: UniqueKeyCondition[T]) {
  def toDynamoObject: DynamoObject = T.toDynamoObject(t)
}

trait UniqueKeyConditions[T] {
  def toDynamoObject(t: T): Set[DynamoObject]
}

object UniqueKeyConditions {
  def apply[T](implicit U: UniqueKeyConditions[T]): UniqueKeyConditions[T] = U

  implicit def keyList[V: DynamoFormat] = new UniqueKeyConditions[KeyList[V]] {
    final def toDynamoObject(kl: KeyList[V]) =
      kl.values.map(v => DynamoObject(kl.key.placeholder("") -> v))
  }

  implicit def multipleKeyList[H: DynamoFormat, R: DynamoFormat] =
    new UniqueKeyConditions[MultipleKeyList[H, R]] {
      final def toDynamoObject(mkl: MultipleKeyList[H, R]) = {
        val (hashKey, rangeKey) = mkl.keys
        mkl.values.map {
          case (h, r) =>
            DynamoObject(hashKey.placeholder("") -> h) <> DynamoObject(rangeKey.placeholder("") -> r)
        }
      }
    }
}

case class UniqueKeys[T](t: T)(implicit K: UniqueKeyConditions[T]) {
  def toDynamoObject: Set[DynamoObject] = K.toDynamoObject(t)
}

case class KeyList[T: DynamoFormat](key: AttributeName, values: Set[T])
case class MultipleKeyList[H: DynamoFormat, R: DynamoFormat](keys: (AttributeName, AttributeName), values: Set[(H, R)])
