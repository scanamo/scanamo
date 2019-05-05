package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.util.{Map => JMap, HashMap}

sealed abstract class DynamoObject extends Product with Serializable { self =>
  import DynamoObject._

  final def apply: JMap[String, AttributeValue] = self match {
    case Strict(xs) => xs
    case Pure(xs) => unsafeToMap(xs)
    case Concat(xs, ys) => unsafeMerge(xs.apply, ys.apply)
  }

  final def get(key: String): Option[AttributeValue] = self match {
    case Strict(xs) => Option(xs.get(key))
    case Pure(xs) => xs.get(key).map(_.toAttributeValue)
    case Concat(xs, ys) => xs.get(key) orElse ys.get(key)
  }

  final def toDynamoValue: DynamoValue = DynamoValue.fromDynamoObject(self)

  final def toAttributeValue: AttributeValue = ???

  final def toExpressionAttributeValues: JMap[String, AttributeValue] = self match {
    case Strict(xs) => unsafeToJMap { m => xs.entrySet.stream.forEach({x => m.put(":" ++ x.getKey, x.getValue); ()}) }
    case Pure(xs) => unsafeToJMap(m => xs foreach { case (k, x) => m.put(":" ++ k, x.toAttributeValue) })
    case Concat(xs, ys) => unsafeMerge(xs.toExpressionAttributeValues, ys.toExpressionAttributeValues)
  }

  final def <>(that: DynamoObject): DynamoObject = Concat(self, that)
}

object DynamoObject {
  final case class Strict(xs: JMap[String, AttributeValue]) extends DynamoObject
  final case class Pure(xs: Map[String, DynamoValue]) extends DynamoObject
  final case class Concat(xs: DynamoObject, ys: DynamoObject) extends DynamoObject

  def apply(xs: JMap[String, AttributeValue]): DynamoObject = Strict(xs)
  def apply(xs: (String, DynamoValue)*): DynamoObject = apply(xs.toMap)
  def apply(xs: Map[String, DynamoValue]): DynamoObject = Pure(xs)
  def apply[A](xs: (String, A)*)(implicit D: DynamoFormat[A]): DynamoObject = apply(xs.foldLeft(Map.empty[String, DynamoValue]) { case (m, (k, x)) => m + (k -> D.write(x)) })

  private[DynamoObject] def unsafeToMap(m: Map[String, DynamoValue]): JMap[String, AttributeValue] = {
    val n = new HashMap[String, AttributeValue]
    m foreach { case (k, x) => n.put(k, x.toAttributeValue) }
    n
  }

  private[DynamoObject] def unsafeToJMap(f: JMap[String, AttributeValue] => Unit): JMap[String, AttributeValue] = {
    val m = new java.util.HashMap[String, AttributeValue]
    f(m) 
    m
  }

  private[DynamoObject] def unsafeMerge[K, V](xs: JMap[K, V], ys: JMap[K, V]): JMap[K, V] =
    ys.entrySet.stream.reduce[JMap[K, V]](
      xs,
      (acc, x) => { 
        acc.put(x.getKey, x.getValue)
        acc
      },
      unsafeMerge
    )
}