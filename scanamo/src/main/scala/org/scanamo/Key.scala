package org.scanamo

import cats.instances.option._
import cats.syntax.apply._
import org.scanamo.query.AttributeName

sealed trait KeyType
sealed trait Simple extends KeyType
sealed trait Composite extends KeyType

sealed abstract class Key[KT <: KeyType, +A] extends Product with Serializable { self =>
  def toDynamoObject: DynamoObject

  final def &&[A1 >: A: DynamoFormat, B: DynamoFormat](
    that: Key[Simple, B]
  )(implicit ev: Key[KT, A] <:< Key[Simple, A1]): Key[Composite, (A1, B)] =
    Key.And(ev(self), that)
}

object Key {
  final private case class Equals[A](k: AttributeName, v: A)(implicit D: DynamoFormat[A]) extends Key[Simple, A] {
    final def toDynamoObject: DynamoObject = DynamoObject(k.toString -> v)
  }

  final private case class And[A: DynamoFormat, B: DynamoFormat](p: Key[Simple, A], s: Key[Simple, B])
      extends Key[Composite, (A, B)] {
    final def toDynamoObject: DynamoObject = p.toDynamoObject <> s.toDynamoObject
  }

  def apply[A: DynamoFormat: IsSimpleKey](k: AttributeName, v: A): Key[Simple, A] = Equals(k, v)

  def fromDynamoObject[A: DynamoFormat: IsSimpleKey](key: AttributeName, obj: DynamoObject): Option[Key[Simple, A]] =
    obj(key.toString).flatMap(_.as[A].toOption).map(Key(key, _))

  def fromDynamoObject[A: DynamoFormat: IsSimpleKey, B: DynamoFormat: IsSimpleKey](
    keys: (AttributeName, AttributeName),
    obj: DynamoObject
  ): Option[Key[Composite, (A, B)]] =
    (
      obj(keys._1.toString).flatMap(_.as[A].toOption),
      obj(keys._2.toString).flatMap(_.as[B].toOption)
    ).mapN(Key(keys._1, _) && Key(keys._2, _))

}
