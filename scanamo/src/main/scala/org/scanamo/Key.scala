package org.scanamo

import cats.instances.option._
import cats.syntax.apply._
import org.scanamo.query.AttributeName

sealed trait KeyType
sealed trait PartitionType extends KeyType
sealed trait SortType extends KeyType

sealed abstract class Key[KT <: KeyType, +A] extends Product with Serializable { self =>
  def toDynamoObject: DynamoObject

  final def &&[A1 >: A: DynamoFormat: SimpleKey, B: DynamoFormat: SimpleKey](
    that: (AttributeName, B)
  )(implicit ev: Key[KT, A] <:< Key[PartitionType, A1]): Key[PartitionType with SortType, (A1, B)] =
    Key.And(ev(self), Key.sort(that._1, that._2))

  final def &&[A1 >: A: DynamoFormat: SimpleKey, B: DynamoFormat: SimpleKey](
    that: Key[SortType, B]
  )(implicit ev: Key[KT, A] <:< Key[PartitionType, A1]): Key[PartitionType with SortType, (A1, B)] =
    Key.And(ev(self), that)
}

object Key {
  final private case class Partition[A: DynamoFormat: SimpleKey](k: AttributeName, v: A) extends Key[PartitionType, A] {
    final def toDynamoObject: DynamoObject = DynamoObject(k.toString -> v)
  }

  final private case class Sort[A: DynamoFormat: SimpleKey](k: AttributeName, v: A) extends Key[SortType, A] {
    final def toDynamoObject: DynamoObject = DynamoObject(k.toString -> v)
  }

  final private case class And[A: DynamoFormat, B: DynamoFormat](p: Key[PartitionType, A], s: Key[SortType, B])
      extends Key[PartitionType with SortType, (A, B)] {
    final def toDynamoObject: DynamoObject = p.toDynamoObject <> s.toDynamoObject
  }

  def apply[A: DynamoFormat: SimpleKey](k: AttributeName, v: A): Key[PartitionType, A] = Partition(k, v)

  def partition[A: DynamoFormat: SimpleKey](k: AttributeName, v: A): Key[PartitionType, A] = apply(k, v)

  def sort[A: DynamoFormat: SimpleKey](k: AttributeName, v: A): Key[SortType, A] = Sort(k, v)

  def fromDynamoObject[A: DynamoFormat: SimpleKey](key: AttributeName,
                                                   obj: DynamoObject): Option[Key[PartitionType, A]] =
    obj(key.toString).flatMap(_.as[A].toOption).map(Key(key, _))

  def fromDynamoObject[A: DynamoFormat: SimpleKey, B: DynamoFormat: SimpleKey](
    keys: (AttributeName, AttributeName),
    obj: DynamoObject
  ): Option[Key[PartitionType with SortType, (A, B)]] =
    (
      obj(keys._1.toString).flatMap(_.as[A].toOption),
      obj(keys._2.toString).flatMap(_.as[B].toOption)
    ).mapN(Key(keys._1, _) && Key.sort(keys._2, _))

}
