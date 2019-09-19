package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.util.{ Map => JMap, HashMap }
import org.scanamo.error.DynamoReadError
import cats.syntax.apply._
import cats.instances.either._
import cats.instances.option._

/**
  * A `DynamoObject` is a map of strings to values that can be embedded into
  * an `AttributeValue`.
  */
sealed abstract class DynamoObject extends Product with Serializable { self =>
  import DynamoObject._

  protected def internalToMap: Map[String, DynamoValue]

  /**
    * Gets the value mapped to `key` if it exists
    */
  final def apply(key: String): Option[DynamoValue] = self match {
    case Empty          => None
    case Strict(xs)     => Option(xs.get(key)).map(DynamoValue.fromAttributeValue)
    case Pure(xs)       => xs.get(key)
    case Concat(xs, ys) => xs(key) orElse ys(key)
  }

  /**
    * Gets the size of the map
    */
  final def size: Int = self match {
    case Empty          => 0
    case Strict(xs)     => xs.size
    case Pure(xs)       => xs.size
    case Concat(xs, ys) => xs.size + ys.size
  }

  /**
    * Checks if the map is empty
    */
  final def isEmpty: Boolean = self match {
    case Empty => true
    case _     => false
  }

  /**
    * Chekcs if the map is not empty
    */
  final def nonEmpty: Boolean = !isEmpty

  /**
    * Checks if the map contains a certain `key`
    */
  final def contains(key: String): Boolean = self match {
    case Empty          => false
    case Strict(xs)     => xs.containsKey(key)
    case Pure(xs)       => xs.contains(key)
    case Concat(xs, ys) => xs.contains(key) || ys.contains(key)
  }

  /**
    * Gets the list of keys in the map
    */
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

  /**
    * Gets the list of values in the map
    */
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

  /**
    * Transforms the map by assigning values to new keys
    */
  final def mapKeys(f: String => String): DynamoObject = self match {
    case Empty          => Empty
    case Strict(xs)     => Pure(unsafeToScalaMap(xs).map { case (k, v) => f(k) -> DynamoValue.fromAttributeValue(v) })
    case Pure(xs)       => Pure(xs.map { case (k, v) => f(k) -> v })
    case Concat(xs, ys) => Concat(xs.mapKeys(f), ys.mapKeys(f))
  }

  // TODO: find a better name
  // TODO: mark as private?
  final def toJavaMap: JMap[String, AttributeValue] = self match {
    case Empty          => unsafeToJavaMap(Map.empty)
    case Strict(xs)     => xs
    case Pure(xs)       => unsafeToJavaMap(xs.mapValues(_.toAttributeValue).toMap)
    case Concat(xs, ys) => unsafeMerge(xs.toJavaMap, ys.toJavaMap)
  }

  /**
    * Make a value out of this map
    */
  final def toDynamoValue: DynamoValue = DynamoValue.fromDynamoObject(self)

  /**
    * Make an AWS SDK value out of this map
    */
  final def toAttributeValue: AttributeValue = new AttributeValue().withM(toJavaMap)

  /**
    * Builds a [[scala.collection.Map]] if this map is made entirely of values of type `V`
    */
  final def toMap[V](implicit D: DynamoFormat[V]): Either[DynamoReadError, Map[String, V]] = self match {
    case Empty => Right(Map.empty)
    case Strict(xs) =>
      xs.entrySet.stream.reduce[Either[DynamoReadError, Map[String, V]]](
        Right(Map.empty),
        (xs0, e) => (xs0, D.read(e.getValue)).mapN((xs, x) => xs + (e.getKey -> x)),
        (xs0, ys0) => (xs0, ys0).mapN(_ ++ _)
      )
    case Pure(xs) =>
      xs.foldLeft[Either[DynamoReadError, Map[String, V]]](Right(Map.empty)) {
        case (e @ Left(_), _)   => e
        case (Right(m), (k, x)) => D.read(x).right.map(x => m + (k -> x))
      }
    case Concat(xs0, ys0) => (xs0.toMap, ys0.toMap).mapN(_ ++ _)
  }

  /**
    * Builds a map where the keys are transformed to match the convention for
    * expression attribute values in DynamoDB operations
    * See [[https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ExpressionAttributeValues.html]]
    */
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
      (xs.toExpressionAttributeValues, ys.toExpressionAttributeValues).mapN(unsafeMerge)
  }

  /**
    * Add an entry to the map or overwrites the existing value if there
    */
  final def add(key: String, value: DynamoValue): DynamoObject = self match {
    case Empty                            => Pure(Map(key -> value))
    case xs: Strict                       => Concat(singleton(key, value), xs)
    case Pure(xs)                         => Pure(xs + (key -> value))
    case o @ Concat(Strict(_), Strict(_)) => Concat(singleton(key, value), o)
    case Concat(Strict(xs), ys)           => Concat(Strict(xs), ys.add(key, value))
    case Concat(xs, Strict(ys))           => Concat(xs.add(key, value), Strict(ys))
    case Concat(xs, ys)                   => Concat(xs.add(key, value), ys)
  }

  /**
    * Remove an entry from the map if there
    */
  final def remove(key: String): DynamoObject = self match {
    case Empty                              => Empty
    case Strict(xs)                         => Pure((unsafeToScalaMap(xs) - key).mapValues(DynamoValue.fromAttributeValue).toMap)
    case Pure(xs)                           => Pure(xs - key)
    case Concat(xs, ys) if xs.contains(key) => Concat(xs.remove(key), ys)
    case Concat(xs, ys)                     => Concat(xs, ys.remove(key))
  }

  /**
    * Operator alias for `add`
    */
  final def +(x: (String, DynamoValue)): DynamoObject = self.add(x._1, x._2)

  /**
    * Operator alias for `remove`
    */
  final def -(key: String): DynamoObject = self remove key

  /**
    * Concatenates two maps
    */
  final def concat(that: DynamoObject): DynamoObject =
    if (self.isEmpty) that
    else if (that.isEmpty) self
    else Concat(self, that)

  /**
    * Operator alias for `concat`
    */
  final def <>(that: DynamoObject): DynamoObject = self concat that

  final override def equals(that: Any): Boolean =
    that.isInstanceOf[DynamoObject] && (internalToMap == that.asInstanceOf[DynamoObject].internalToMap)

  final override def hashCode(): Int = internalToMap.hashCode
}

object DynamoObject {
  private[DynamoObject] case object Empty extends DynamoObject {
    final def internalToMap = Map.empty
  }
  final private[DynamoObject] case class Strict(xs: JMap[String, AttributeValue]) extends DynamoObject {
    final def internalToMap = unsafeToScalaMap(xs).mapValues(DynamoValue.fromAttributeValue).toMap
  }
  final private[DynamoObject] case class Pure(xs: Map[String, DynamoValue]) extends DynamoObject {
    final def internalToMap = xs
  }
  final private[DynamoObject] case class Concat(xs: DynamoObject, ys: DynamoObject) extends DynamoObject {
    final def internalToMap = xs.internalToMap ++ ys.internalToMap
  }

  /**
    * Builds a map from a Java Map of attribute values
    */
  def apply(xs: JMap[String, AttributeValue]): DynamoObject = if (xs.isEmpty) Empty else Strict(xs)

  /**
    * Builds a map from an arbitrary number of `(String, DynamoValue)` pairs
    */
  def apply(xs: (String, DynamoValue)*): DynamoObject = apply(xs.toMap)

  /**
    * Builds a map from a [[scala.collection.Map]] of values
    */
  def apply(xs: Map[String, DynamoValue]): DynamoObject = if (xs.isEmpty) Empty else Pure(xs)

  /**
    * Builds a map from an arbitrary number of `(String, A)` pairs for any `A` that can be
    * turned into a value
    */
  def apply[A](xs: (String, A)*)(implicit D: DynamoFormat[A]): DynamoObject =
    apply(xs.foldLeft(Map.empty[String, DynamoValue]) { case (m, (k, x)) => m + (k -> D.write(x)) })

  /**
    * The empty map
    */
  val empty: DynamoObject = Empty

  /**
    * Builds a singleton map
    */
  def singleton(key: String, x: DynamoValue): DynamoObject = Pure(Map(key -> x))

  /**
    * Builds a map from an iterable collection of `(String, DynamoValue)` pairs
    */
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
