package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.util.Collection
import java.nio.ByteBuffer
import java.util.stream.Collectors

sealed abstract class DynamoArray extends Product with Serializable { self =>
  import DynamoArray._

  def size: Int = self match {
    case Empty          => 0
    case Strict(xs)     => xs.size
    case StrictN(xs)    => xs.size
    case StrictS(xs)    => xs.size
    case StrictB(xs)    => xs.size
    case Pure(xs)       => xs.size
    case PureN(xs)      => xs.size
    case PureS(xs)      => xs.size
    case PureB(xs)      => xs.size
    case Concat(xs, ys) => xs.size + ys.size
  }

  def isEmpty: Boolean = self match {
    case Empty => true
    case _     => false
  }

  def nonEmpty: Boolean = !isEmpty

  def contains(dv: DynamoValue): Boolean = self match {
    case Empty          => false
    case Strict(xs)     => xs.contains(dv.toAttributeValue)
    case Pure(xs)       => xs.contains(dv)
    case StrictS(xs)    => dv.asString.map(xs.contains(_)).getOrElse(false)
    case PureS(xs)      => dv.asString.map(xs.contains(_)).getOrElse(false)
    case StrictN(xs)    => dv.asNumber.map(xs.contains(_)).getOrElse(false)
    case PureN(xs)      => dv.asNumber.map(xs.contains(_)).getOrElse(false)
    case StrictB(xs)    => dv.asByteBuffer.map(xs.contains(_)).getOrElse(false)
    case PureB(xs)      => dv.asByteBuffer.map(xs.contains(_)).getOrElse(false)
    case Concat(xs, ys) => xs.contains(dv) || ys.contains(dv)
  }

  def toAttributeValue: AttributeValue = self match {
    case Empty       => DynamoValue.Null
    case Strict(xs)  => new AttributeValue().withL(xs)
    case StrictS(xs) => new AttributeValue().withSS(xs)
    case StrictN(xs) => new AttributeValue().withNS(xs)
    case StrictB(xs) => new AttributeValue().withBS(xs)
    case Pure(xs)    => new AttributeValue().withL(unsafeToCollection[DynamoValue, AttributeValue](xs, _.toAttributeValue))
    case PureS(xs)   => new AttributeValue().withSS(unsafeToCollection(xs, identity[String]))
    case PureN(xs)   => new AttributeValue().withNS(unsafeToCollection(xs, identity[String]))
    case PureB(xs)   => new AttributeValue().withBS(unsafeToCollection(xs, identity[ByteBuffer]))
    case Concat(xs, ys) =>
      new AttributeValue().withL(unsafeMerge[AttributeValue](xs.toJavaCollection, ys.toJavaCollection))
  }

  def toJavaCollection: Collection[AttributeValue] = self match {
    case Empty          => new java.util.LinkedList[AttributeValue]()
    case Strict(xs)     => xs
    case StrictS(xs)    => xs.stream.map[AttributeValue](new AttributeValue().withS(_)).collect(Collectors.toList())
    case StrictN(xs)    => xs.stream.map[AttributeValue](new AttributeValue().withN(_)).collect(Collectors.toList())
    case StrictB(xs)    => xs.stream.map[AttributeValue](new AttributeValue().withB(_)).collect(Collectors.toList())
    case Pure(xs)       => unsafeToCollection[DynamoValue, AttributeValue](xs, _.toAttributeValue)
    case PureS(xs)      => unsafeToCollection(xs, new AttributeValue().withS(_))
    case PureN(xs)      => unsafeToCollection(xs, new AttributeValue().withN(_))
    case PureB(xs)      => unsafeToCollection(xs, new AttributeValue().withB(_))
    case Concat(xs, ys) => unsafeMerge(xs.toJavaCollection, ys.toJavaCollection)
  }

  def toDynamoValue: DynamoValue = DynamoValue.fromDynamoArray(self)

  def isAttributeValueArray: Boolean = self match {
    case _: Strict | _: Pure => true
    case Concat(xs, ys)      => xs.isAttributeValueArray && ys.isAttributeValueArray
    case _                   => false
  }

  def isStringArray: Boolean = self match {
    case _: StrictS | _: PureS => true
    case Concat(xs, ys)        => xs.isStringArray && ys.isStringArray
    case _                     => false
  }

  def isNumArray: Boolean = self match {
    case _: StrictN | _: PureN => true
    case Concat(xs, ys)        => xs.isNumArray && ys.isNumArray
    case _                     => false
  }

  def isByteBufferArray: Boolean = self match {
    case _: StrictB | _: PureB => true
    case Concat(xs, ys)        => xs.isByteBufferArray && ys.isByteBufferArray
    case _                     => false
  }

  def asArray: Option[List[DynamoValue]] = self match {
    case Empty => Some(List.empty)
    case Strict(xs0) =>
      Some(
        xs0.stream.reduce[List[DynamoValue]](
          Nil,
          (xs, x) => xs :+ DynamoValue.fromAttributeValue(x),
          _ ++ _
        )
      )
    case Pure(xs) => Some(xs)
    case Concat(xs0, ys0) =>
      for {
        xs <- xs0.asArray
        ys <- ys0.asArray
      } yield xs ++ ys
    case _ => None
  }

  def asStringArray: Option[List[String]] = self match {
    case Empty       => Some(List.empty)
    case StrictS(xs) => Some(xs.stream.reduce[List[String]](Nil, _ :+ _, _ ++ _))
    case PureS(xs)   => Some(xs)
    case Concat(xs0, ys0) =>
      for {
        xs <- xs0.asStringArray
        ys <- ys0.asStringArray
      } yield xs ++ ys
    case _ => None
  }

  def asNumArray: Option[List[String]] = self match {
    case Empty       => Some(List.empty)
    case StrictN(xs) => Some(xs.stream.reduce[List[String]](Nil, _ :+ _, _ ++ _))
    case PureN(xs)   => Some(xs)
    case Concat(xs0, ys0) =>
      for {
        xs <- xs0.asNumArray
        ys <- ys0.asNumArray
      } yield xs ++ ys
    case _ => None
  }

  def asByteBufferArray: Option[List[ByteBuffer]] = self match {
    case Empty       => Some(List.empty)
    case StrictB(xs) => Some(xs.stream.reduce[List[ByteBuffer]](Nil, _ :+ _, _ ++ _))
    case PureB(xs)   => Some(xs)
    case Concat(xs0, ys0) =>
      for {
        xs <- xs0.asByteBufferArray
        ys <- ys0.asByteBufferArray
      } yield xs ++ ys
    case _ => None
  }

  def <>(that: DynamoArray): DynamoArray = self match {
    case Empty => that
    case _ =>
      that match {
        case Empty => self
        case _     => DynamoArray.Concat(self, that)
      }
  }
}

object DynamoArray {
  private[DynamoArray] case object Empty extends DynamoArray
  private[DynamoArray] final case class Strict(xs: Collection[AttributeValue]) extends DynamoArray
  private[DynamoArray] final case class StrictS(xs: Collection[String]) extends DynamoArray
  private[DynamoArray] final case class StrictN(xs: Collection[String]) extends DynamoArray
  private[DynamoArray] final case class StrictB(xs: Collection[ByteBuffer]) extends DynamoArray
  private[DynamoArray] final case class Pure(xs: List[DynamoValue]) extends DynamoArray
  private[DynamoArray] final case class PureS(xs: List[String]) extends DynamoArray
  private[DynamoArray] final case class PureN(xs: List[String]) extends DynamoArray
  private[DynamoArray] final case class PureB(xs: List[ByteBuffer]) extends DynamoArray
  private[DynamoArray] final case class Concat(xs: DynamoArray, ys: DynamoArray) extends DynamoArray

  private[DynamoArray] def unsafeMerge[A](xs: Collection[A], ys: Collection[A]): Collection[A] =
    java.util.stream.Stream.concat[A](xs.stream, ys.stream).collect(Collectors.toList())

  private[DynamoArray] def unsafeToCollection[A, B](xs: List[A], f: A => B): Collection[B] = {
    val l = new java.util.ArrayList[B](xs.size)
    xs foreach { x =>
      l.add(f(x))
    }
    l
  }

  def apply(xs: Collection[AttributeValue]): DynamoArray = if (xs.isEmpty) Empty else Strict(xs)
  def apply(xs: DynamoValue*): DynamoArray = if (xs.isEmpty) Empty else Pure(xs.toList)
  def strings(xs: Collection[String]): DynamoArray = if (xs.isEmpty) Empty else StrictS(xs)
  def strings(xs: String*): DynamoArray = if (xs.isEmpty) Empty else PureS(xs.toList)
  def numbers(xs: Collection[String]): DynamoArray = if (xs.isEmpty) Empty else StrictN(xs)
  def numbers[N: Numeric](xs: N*): DynamoArray = if (xs.isEmpty) Empty else PureN(xs.toList.map(_.toString))
  def byteBuffers(xs: Collection[ByteBuffer]): DynamoArray = if (xs.isEmpty) Empty else StrictB(xs)
  def byteBuffers(xs: ByteBuffer*): DynamoArray = if (xs.isEmpty) Empty else PureB(xs.toList)
}
