package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.util.{Map => JMap, HashMap}
import org.scanamo.error.{ DynamoReadError, MissingProperty }
import org.scanamo.internal._
import cats.syntax.either._

sealed abstract class DynamoObject extends Product with Serializable { self =>
  import DynamoObject._

  final def apply(key: String): Option[DynamoValue] = self match {
    case Empty          => None
    case Strict(xs)     => Option(xs.get(key)).map(DynamoValue.fromAttributeValue)
    case Pure(xs)       => xs.get(key)
    case Concat(xs, ys) => xs(key) orElse ys(key)
  }

  final def := (key: String): Either[DynamoReadError, DynamoValue] = self match {
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
        jfun2[Either[DynamoReadError, Map[String, V]], JMap.Entry[String, AttributeValue], Either[DynamoReadError, Map[String, V]]]((xs0, e) => for { xs <- xs0; x <- D.read(e.getValue) } yield xs + (e.getKey -> x)),
        jbiop[Either[DynamoReadError, Map[String, V]]]((xs0, ys0) => for { xs <- xs0; ys <- ys0 } yield xs ++ ys)
      )
    case Pure(xs) =>
      xs.foldLeft[Either[DynamoReadError, Map[String, V]]](Right(Map.empty)) {
        case (e @ Left(_), _)   => e
        case (Right(m), (k, x)) => D.read(x).map(x => m + (k -> x))
      }
    case Concat(xs0, ys0) => for { xs <- xs0.toMap; ys <- ys0.toMap } yield xs ++ ys
  }

  final def toExpressionAttributeValues: Option[JMap[String, AttributeValue]] = self match {
    case Empty => None
    case Strict(xs) =>
      Some(unsafeTransform { m =>
        xs.entrySet.stream.forEach(jcon[JMap.Entry[String, AttributeValue]]{ x =>
          m.put(":" ++ x.getKey, x.getValue); ()
        })
      })
    case Pure(xs) => Some(unsafeTransform(m => xs foreach { case (k, x) => m.put(":" ++ k, x.toAttributeValue) }))
    case Concat(xs, ys) =>
      for { xss <- xs.toExpressionAttributeValues; yss <- ys.toExpressionAttributeValues } yield unsafeMerge(xss, yss)
  }

  final def <>(that: DynamoObject): DynamoObject = self match {
    case Empty => that
    case _ =>
      that match {
        case Empty => self
        case _     => Concat(self, that)
      }
  }
}

object DynamoObject {
  case object Empty extends DynamoObject
  final case class Strict(xs: JMap[String, AttributeValue]) extends DynamoObject
  final case class Pure(xs: Map[String, DynamoValue]) extends DynamoObject
  final case class Concat(xs: DynamoObject, ys: DynamoObject) extends DynamoObject

  def apply(xs: JMap[String, AttributeValue]): DynamoObject = if (xs.isEmpty) Empty else Strict(xs)
  def apply(xs: (String, DynamoValue)*): DynamoObject = apply(xs.toMap)
  def apply(xs: Map[String, DynamoValue]): DynamoObject = if (xs.isEmpty) Empty else Pure(xs)
  def apply[A](xs: (String, A)*)(implicit D: DynamoFormat[A]): DynamoObject =
    apply(xs.foldLeft(Map.empty[String, DynamoValue]) { case (m, (k, x)) => m + (k -> D.write(x)) })

  val empty: DynamoObject = Empty

  def singleton(key: String, x: DynamoValue): DynamoObject = Pure(Map(key -> x))
  def fromIteralble(xs: Iterable[(String, DynamoValue)]): DynamoObject = apply(xs.toMap)

  private[DynamoObject] def unsafeToJavaMap(m: Map[String, AttributeValue]): JMap[String, AttributeValue] = {
    val n = new HashMap[String, AttributeValue]
    m foreach { case (k, x) => n.put(k, x) }
    n
  }

  private[DynamoObject] def unsafeToScalaMap(m: JMap[String, AttributeValue]): Map[String, AttributeValue] =
    m.entrySet.stream.reduce[Map[String, AttributeValue]](
      Map.empty,
      jfun2[Map[String, AttributeValue], JMap.Entry[String, AttributeValue], Map[String, AttributeValue]]{(n, e) => n + (e.getKey -> e.getValue)},
      jbiop[Map[String, AttributeValue]](_ ++ _)
    )

  private[DynamoObject] def unsafeTransform(f: JMap[String, AttributeValue] => Unit): JMap[String, AttributeValue] = {
    val m = new java.util.HashMap[String, AttributeValue]
    f(m)
    m
  }

  private[DynamoObject] def unsafeMerge[K, V](xs: JMap[K, V], ys: JMap[K, V]): JMap[K, V] = {
    val m = new java.util.HashMap[K, V]
    xs.forEach(jcon2[K, V] { (k, v) =>
      m.put(k, v); ()
    })
    ys.forEach(jcon2[K, V] { (k, v) =>
      m.put(k, v); ()
    })
    m
  }
}
