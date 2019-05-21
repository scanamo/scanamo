package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.util.{Map => JMap, HashMap}
import org.scanamo.error.{DynamoReadError, MissingProperty}
import cats.syntax.either._
import cats.syntax.apply._
import cats.instances.either._

sealed abstract class DynamoObject extends Product with Serializable { self =>
  import DynamoObject._

  final def apply(key: String): Option[DynamoValue] = self match {
    case Empty          => None
    case Strict(xs)     => Option(xs.get(key)).map(DynamoValue.fromAttributeValue)
    case Pure(xs)       => xs.get(key)
    case Concat(xs, ys) => xs(key) orElse ys(key)
  }

  final def :=(key: String): Either[DynamoReadError, DynamoValue] = self match {
    case Empty          => Left(MissingProperty)
    case Strict(xs)     => Either.fromOption(Option(xs.get(key)).map(DynamoValue.fromAttributeValue), MissingProperty)
    case Pure(xs)       => Either.fromOption(xs.get(key), MissingProperty)
    case Concat(xs, ys) => (xs := key) orElse (ys := key)
  }

  final def size: Int = self match {
    case Empty          => 0
    case Strict(xs)     => xs.size
    case Pure(xs)       => xs.size
    case Concat(xs, ys) => xs.size + ys.size
  }

  final def isEmpty: Boolean = self match {
    case Empty => true
    case _     => false
  }

  final def nonEmpty: Boolean = !isEmpty

  final def contains(key: String): Boolean = self match {
    case Empty          => false
    case Strict(xs)     => xs.containsKey(key)
    case Pure(xs)       => xs.contains(key)
    case Concat(xs, ys) => xs.contains(key) || ys.contains(key)
  }

  final def keys: Iterable[String] = self match {
    case Empty => Iterable.empty[String]
    case Strict(xs) =>
      new Iterable[String] {
        final val iterator = new Iterator[String] {
          private[this] val underlying = xs.keySet.iterator
          final def hasNext = underlying.hasNext
          final def next = underlying.next
        }
      }
    case Pure(xs)       => xs.keys
    case Concat(xs, ys) => xs.keys ++ ys.keys
  }

  final def values: Iterable[DynamoValue] = self match {
    case Empty => Iterable.empty[DynamoValue]
    case Strict(xs) =>
      new Iterable[DynamoValue] {
        final val iterator = new Iterator[DynamoValue] {
          private[this] val underlying = xs.values.iterator
          final def hasNext = underlying.hasNext
          final def next = DynamoValue.fromAttributeValue(underlying.next)
        }
      }
    case Pure(xs)       => xs.values
    case Concat(xs, ys) => xs.values ++ ys.values
  }

  final def mapKeys(f: String => String): DynamoObject = self match {
    case Empty          => Empty
    case Strict(xs)     => Pure(unsafeToScalaMap(xs).map { case (k, v) => f(k) -> DynamoValue.fromAttributeValue(v) })
    case Pure(xs)       => Pure(xs.map { case (k, v) => f(k) -> v })
    case Concat(xs, ys) => Concat(xs.mapKeys(f), ys.mapKeys(f))
  }

  final def toJavaMap: JMap[String, AttributeValue] = self match {
    case Empty          => unsafeToJavaMap(Map.empty)
    case Strict(xs)     => xs
    case Pure(xs)       => unsafeToJavaMap(xs.mapValues(_.toAttributeValue))
    case Concat(xs, ys) => unsafeMerge(xs.toJavaMap, ys.toJavaMap)
  }

  final def toDynamoValue: DynamoValue = DynamoValue.fromDynamoObject(self)

  final def toAttributeValue: AttributeValue =
    self match {
      case Empty => DynamoValue.Null
      case _     => new AttributeValue().withM(toJavaMap)
    }

  final def toMap[V](implicit D: DynamoFormat[V]): Either[DynamoReadError, Map[String, V]] = self match {
    case Empty => Right(Map.empty)
    case Strict(xs) =>
      xs.entrySet.stream.reduce[Either[DynamoReadError, Map[String, V]]](
        Right(Map.empty),
        (xs0, e) => for { xs <- xs0; x <- D.read(e.getValue) } yield xs + (e.getKey -> x),
        (xs0, ys0) => (xs0, ys0).mapN(_ ++ _)
      )
    case Pure(xs) =>
      xs.foldLeft[Either[DynamoReadError, Map[String, V]]](Right(Map.empty)) {
        case (e @ Left(_), _)   => e
        case (Right(m), (k, x)) => D.read(x).map(x => m + (k -> x))
      }
    case Concat(xs0, ys0) => (xs0.toMap, ys0.toMap).mapN(_ ++ _)
  }

  def internalToMap: Map[String, DynamoValue]

  final def toExpressionAttributeValues: Option[JMap[String, AttributeValue]] = self match {
    case Empty => None
    case Strict(xs) =>
      Some(unsafeTransform { m =>
        xs.entrySet.stream.forEach { x =>
          m.put(":" ++ x.getKey, x.getValue); ()
        }
      })
    case Pure(xs) => Some(unsafeTransform(m => xs foreach { case (k, x) => m.put(":" ++ k, x.toAttributeValue) }))
    case Concat(xs, ys) =>
      for { xss <- xs.toExpressionAttributeValues; yss <- ys.toExpressionAttributeValues } yield unsafeMerge(xss, yss)
  }

  final def <>(that: DynamoObject): DynamoObject =
    if (self.isEmpty) that
    else if (that.isEmpty) self
    else Concat(self, that)

  override final def equals(that: Any): Boolean =
    that.isInstanceOf[DynamoObject] && (internalToMap == that.asInstanceOf[DynamoObject].internalToMap)

  override final def hashCode(): Int = internalToMap.hashCode
}

object DynamoObject {
  private[DynamoObject] case object Empty extends DynamoObject {
    final def internalToMap = Map.empty
  }
  private[DynamoObject] final case class Strict(xs: JMap[String, AttributeValue]) extends DynamoObject {
    final def internalToMap = unsafeToScalaMap(xs).mapValues(DynamoValue.fromAttributeValue)
  }
  private[DynamoObject] final case class Pure(xs: Map[String, DynamoValue]) extends DynamoObject {
    final def internalToMap = xs
  }
  private[DynamoObject] final case class Concat(xs: DynamoObject, ys: DynamoObject) extends DynamoObject {
    final def internalToMap = xs.internalToMap ++ ys.internalToMap
  }

  def apply(xs: JMap[String, AttributeValue]): DynamoObject = if (xs.isEmpty) Empty else Strict(xs)
  def apply(xs: (String, DynamoValue)*): DynamoObject = apply(xs.toMap)
  def apply(xs: Map[String, DynamoValue]): DynamoObject = if (xs.isEmpty) Empty else Pure(xs)
  def apply[A](xs: (String, A)*)(implicit D: DynamoFormat[A]): DynamoObject =
    apply(xs.foldLeft(Map.empty[String, DynamoValue]) { case (m, (k, x)) => m + (k -> D.write(x)) })

  val empty: DynamoObject = Empty

  def singleton(key: String, x: DynamoValue): DynamoObject = Pure(Map(key -> x))
  def fromIterable(xs: Iterable[(String, DynamoValue)]): DynamoObject = apply(xs.toMap)

  private[DynamoObject] def unsafeToJavaMap(m: Map[String, AttributeValue]): JMap[String, AttributeValue] = {
    val n = new HashMap[String, AttributeValue](m.size, 1)
    m foreach { case (k, x) => n.put(k, x) }
    n
  }

  private[DynamoObject] def unsafeToScalaMap(m: JMap[String, AttributeValue]): Map[String, AttributeValue] = {
    val builder = Map.newBuilder[String, AttributeValue]
    builder.sizeHint(m.size)
    val iterator = m.entrySet.iterator
    while (iterator.hasNext) {
      val e = iterator.next
      builder += e.getKey -> e.getValue
    }
    builder.result()
  }

  private[DynamoObject] def unsafeTransform(f: JMap[String, AttributeValue] => Unit): JMap[String, AttributeValue] = {
    val m = new java.util.HashMap[String, AttributeValue]
    f(m)
    m
  }

  private[DynamoObject] def unsafeMerge[K, V](xs: JMap[K, V], ys: JMap[K, V]): JMap[K, V] = {
    val m = new java.util.HashMap[K, V](xs.size + ys.size)
    m.putAll(xs)
    m.putAll(ys)
    m
  }
}
