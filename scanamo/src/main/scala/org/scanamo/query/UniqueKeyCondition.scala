package org.scanamo.query

import org.scanamo.{DynamoFormat, DynamoObject}
import simulacrum.typeclass

@typeclass trait UniqueKeyCondition[T] {
  type K
  def toDynamoObject(t: T): DynamoObject
  def fromDynamoObject(key: K, dvs: DynamoObject): Option[T]
  def key(t: T): K
}

object UniqueKeyCondition {
  implicit def uniqueEqualsKey[V](implicit V: DynamoFormat[V]) = new UniqueKeyCondition[KeyEquals[V]] {
    type K = Symbol
    final def toDynamoObject(t: KeyEquals[V]) = DynamoObject.singleton(t.key.name, V.write(t.v))
    final def fromDynamoObject(key: K, dvs: DynamoObject) =
      dvs(key.name).flatMap(V.read(_).fold(_ => None, v => Some(KeyEquals(key, v))))
    final def key(t: KeyEquals[V]) = t.key
  }

  implicit def uniqueAndEqualsKey[H: UniqueKeyCondition, R: UniqueKeyCondition] =
    new UniqueKeyCondition[AndEqualsCondition[H, R]] {
      val H = UniqueKeyCondition[H]
      val R = UniqueKeyCondition[R]
      type K = (H.K, R.K)

      final def toDynamoObject(t: AndEqualsCondition[H, R]) =
        H.toDynamoObject(t.hashEquality) <> R.toDynamoObject(t.rangeEquality)

      final def fromDynamoObject(key: K, dvs: DynamoObject) =
        for {
          h <- H.fromDynamoObject(key._1, dvs)
          r <- R.fromDynamoObject(key._2, dvs)
        } yield AndEqualsCondition(h, r)

      final def key(t: AndEqualsCondition[H, R]) = (H.key(t.hashEquality), R.key(t.rangeEquality))
    }
}

case class UniqueKey[T](t: T)(implicit T: UniqueKeyCondition[T]) {
  def toDynamoObject: DynamoObject = T.toDynamoObject(t)
}

@typeclass trait UniqueKeyConditions[T] {
  def toDynamoObject(t: T): Set[DynamoObject]
}

object UniqueKeyConditions {
  implicit def keyList[V](implicit V: DynamoFormat[V]) = new UniqueKeyConditions[KeyList[V]] {
    final def toDynamoObject(kl: KeyList[V]) =
      kl.values.map(v => DynamoObject.singleton(kl.key.name, V.write(v)))
  }

  implicit def multipleKeyList[H, R](implicit H: DynamoFormat[H], R: DynamoFormat[R]) =
    new UniqueKeyConditions[MultipleKeyList[H, R]] {
      final def toDynamoObject(mkl: MultipleKeyList[H, R]) = {
        val (hashKey, rangeKey) = mkl.keys
        mkl.values.map {
          case (h, r) =>
            DynamoObject.singleton(hashKey.name, H.write(h)) <> DynamoObject.singleton(rangeKey.name, R.write(r))
        }
      }
    }
}

case class UniqueKeys[T](t: T)(implicit K: UniqueKeyConditions[T]) {
  def toDynamoObject: Set[DynamoObject] = K.toDynamoObject(t)
}

case class KeyList[T: DynamoFormat](key: Symbol, values: Set[T])
case class MultipleKeyList[H: DynamoFormat, R: DynamoFormat](keys: (Symbol, Symbol), values: Set[(H, R)])
