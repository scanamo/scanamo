package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import cats.syntax.apply._
import cats.instances.option._
import java.nio.ByteBuffer
import java.util.stream.Collectors
import java.{util => ju}

sealed abstract class DynamoArray extends Product with Serializable { self =>
  import DynamoArray._

  protected def toList: List[DynamoValue]

  final def apply(i: Int): Option[DynamoValue] = self match {
    case Empty       => None
    case Strict(xs)  => Option(xs.get(i)).map(DynamoValue.fromAttributeValue)
    case StrictS(xs) => Option(xs.get(i)).map(DynamoValue.fromString)
    case StrictN(xs) => Option(xs.get(i)).map(DynamoValue.unsafeFromNumber)
    case StrictB(xs) => Option(xs.get(i)).map(DynamoValue.fromByteBuffer)
    case Pure(xs)    => xs.drop(i).headOption
    case PureS(xs)   => xs.drop(i).headOption.map(DynamoValue.fromString)
    case PureN(xs)   => xs.drop(i).headOption.map(DynamoValue.unsafeFromNumber)
    case PureB(xs)   => xs.drop(i).headOption.map(DynamoValue.fromByteBuffer)
    case Concat(xs, ys) =>
      val s = xs.size
      if (s > i) xs(i) else ys(i - s)
  }

  final def size: Int = self match {
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

  final def isEmpty: Boolean = self match {
    case Empty => true
    case _     => false
  }

  final def nonEmpty: Boolean = !isEmpty

  final def contains(dv: DynamoValue): Boolean = self match {
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

  final def toAttributeValue: AttributeValue = self match {
    case Empty       => DynamoValue.Null
    case Strict(xs)  => new AttributeValue().withL(xs)
    case StrictS(xs) => new AttributeValue().withSS(xs)
    case StrictN(xs) => new AttributeValue().withNS(xs)
    case StrictB(xs) => new AttributeValue().withBS(xs)
    case Pure(xs)    => new AttributeValue().withL(unsafeToList[DynamoValue, AttributeValue](xs, _.toAttributeValue))
    case PureS(xs)   => new AttributeValue().withSS(unsafeToList(xs, identity[String]))
    case PureN(xs)   => new AttributeValue().withNS(unsafeToList(xs, identity[String]))
    case PureB(xs)   => new AttributeValue().withBS(unsafeToList(xs, identity[ByteBuffer]))
    case Concat(xs, ys) =>
      new AttributeValue().withL(unsafeMerge[AttributeValue](xs.toJavaCollection, ys.toJavaCollection))
  }

  final def toJavaCollection: ju.List[AttributeValue] = self match {
    case Empty          => new java.util.LinkedList[AttributeValue]()
    case Strict(xs)     => xs
    case StrictS(xs)    => xs.stream.map[AttributeValue](new AttributeValue().withS(_)).collect(Collectors.toList())
    case StrictN(xs)    => xs.stream.map[AttributeValue](new AttributeValue().withN(_)).collect(Collectors.toList())
    case StrictB(xs)    => xs.stream.map[AttributeValue](new AttributeValue().withB(_)).collect(Collectors.toList())
    case Pure(xs)       => unsafeToList[DynamoValue, AttributeValue](xs, _.toAttributeValue)
    case PureS(xs)      => unsafeToList[String, AttributeValue](xs, new AttributeValue().withS(_))
    case PureN(xs)      => unsafeToList[String, AttributeValue](xs, new AttributeValue().withN(_))
    case PureB(xs)      => unsafeToList[ByteBuffer, AttributeValue](xs, new AttributeValue().withB(_))
    case Concat(xs, ys) => unsafeMerge(xs.toJavaCollection, ys.toJavaCollection)
  }

  final def toDynamoValue: DynamoValue = DynamoValue.fromDynamoArray(self)

  final def isAttributeValueArray: Boolean = self match {
    case _: Strict | _: Pure => true
    case Concat(xs, ys)      => xs.isAttributeValueArray && ys.isAttributeValueArray
    case _                   => false
  }

  final def isStringArray: Boolean = self match {
    case _: StrictS | _: PureS => true
    case Concat(xs, ys)        => xs.isStringArray && ys.isStringArray
    case _                     => false
  }

  final def isNumArray: Boolean = self match {
    case _: StrictN | _: PureN => true
    case Concat(xs, ys)        => xs.isNumArray && ys.isNumArray
    case _                     => false
  }

  final def isByteBufferArray: Boolean = self match {
    case _: StrictB | _: PureB => true
    case Concat(xs, ys)        => xs.isByteBufferArray && ys.isByteBufferArray
    case _                     => false
  }

  final def asArray: Option[List[DynamoValue]] = self match {
    case Empty => Some(List.empty)
    case Strict(xs0) =>
      Some(
        xs0.stream.reduce[List[DynamoValue]](
          Nil,
          (xs, x) => xs :+ DynamoValue.fromAttributeValue(x),
          _ ++ _
        )
      )
    case Pure(xs)         => Some(xs)
    case Concat(xs0, ys0) => (xs0.asArray, ys0.asArray).mapN(_ ++ _)
    case _                => None
  }

  final def asStringArray: Option[List[String]] = self match {
    case Empty            => Some(List.empty)
    case StrictS(xs)      => Some(xs.stream.reduce[List[String]](Nil, _ :+ _, _ ++ _))
    case PureS(xs)        => Some(xs)
    case Concat(xs0, ys0) => (xs0.asStringArray, ys0.asStringArray).mapN(_ ++ _)
    case _                => None
  }

  final def asNumArray: Option[List[String]] = self match {
    case Empty            => Some(List.empty)
    case StrictN(xs)      => Some(xs.stream.reduce[List[String]](Nil, _ :+ _, _ ++ _))
    case PureN(xs)        => Some(xs)
    case Concat(xs0, ys0) => (xs0.asNumArray, ys0.asNumArray).mapN(_ ++ _)
    case _                => None
  }

  final def asByteBufferArray: Option[List[ByteBuffer]] = self match {
    case Empty            => Some(List.empty)
    case StrictB(xs)      => Some(xs.stream.reduce[List[ByteBuffer]](Nil, _ :+ _, _ ++ _))
    case PureB(xs)        => Some(xs)
    case Concat(xs0, ys0) => (xs0.asByteBufferArray, ys0.asByteBufferArray).mapN(_ ++ _)
    case _                => None
  }

  final def <>(that: DynamoArray): DynamoArray =
    if (self.isEmpty) that
    else if (that.isEmpty) self
    else Concat(self, that)

  override final def equals(that: Any): Boolean =
    that.isInstanceOf[DynamoArray] && (toList == that.asInstanceOf[DynamoArray].toList)

  override final def hashCode(): Int = toList.hashCode
}

object DynamoArray {
  private[DynamoArray] case object Empty extends DynamoArray {
    final def toList: List[DynamoValue] = Nil
  }
  private[DynamoArray] final case class Strict(xs: ju.List[AttributeValue]) extends DynamoArray {
    final def toList: List[DynamoValue] = unsafeToScalaList(xs, DynamoValue.fromAttributeValue)
  }
  private[DynamoArray] final case class StrictS(xs: ju.List[String]) extends DynamoArray {
    final def toList: List[DynamoValue] = unsafeToScalaList(xs, DynamoValue.fromString)
  }
  private[DynamoArray] final case class StrictN(xs: ju.List[String]) extends DynamoArray {
    final def toList: List[DynamoValue] = unsafeToScalaList(xs, DynamoValue.unsafeFromNumber)
  }
  private[DynamoArray] final case class StrictB(xs: ju.List[ByteBuffer]) extends DynamoArray {
    final def toList: List[DynamoValue] = unsafeToScalaList(xs, DynamoValue.fromByteBuffer)
  }
  private[DynamoArray] final case class Pure(xs: List[DynamoValue]) extends DynamoArray {
    final def toList: List[DynamoValue] = xs
  }
  private[DynamoArray] final case class PureS(xs: List[String]) extends DynamoArray {
    final def toList: List[DynamoValue] = xs.map(DynamoValue.fromString)
  }
  private[DynamoArray] final case class PureN(xs: List[String]) extends DynamoArray {
    final def toList: List[DynamoValue] = xs.map(DynamoValue.unsafeFromNumber)
  }
  private[DynamoArray] final case class PureB(xs: List[ByteBuffer]) extends DynamoArray {
    final def toList: List[DynamoValue] = xs.map(DynamoValue.fromByteBuffer)
  }
  private[DynamoArray] final case class Concat(xs: DynamoArray, ys: DynamoArray) extends DynamoArray {
    final def toList: List[DynamoValue] = xs.toList ++ ys.toList
  }

  private[DynamoArray] def unsafeMerge[A](xs: ju.List[A], ys: ju.List[A]): ju.List[A] =
    java.util.stream.Stream.concat[A](xs.stream, ys.stream).collect(Collectors.toList())

  private[DynamoArray] def unsafeToList[A, B](xs: List[A], f: A => B): ju.List[B] = {
    val l = new java.util.ArrayList[B](xs.size)
    xs foreach { x =>
      l.add(f(x))
    }
    l
  }

  private[DynamoArray] def unsafeToScalaList[A, B](xs: ju.List[A], f: A => B): List[B] = {
    val builder = List.newBuilder[B]
    builder.sizeHint(xs.size)
    xs.forEach(x => builder += f(x))
    builder.result()
  }

  def apply(xs: ju.List[AttributeValue]): DynamoArray = if (xs.isEmpty) Empty else Strict(xs)
  def apply(xs: DynamoValue*): DynamoArray = if (xs.isEmpty) Empty else Pure(xs.toList)
  def strings(xs: ju.List[String]): DynamoArray = if (xs.isEmpty) Empty else StrictS(xs)
  def strings(xs: String*): DynamoArray = if (xs.isEmpty) Empty else PureS(xs.toList)
  def numbers(xs: ju.List[String]): DynamoArray = if (xs.isEmpty) Empty else StrictN(xs)
  def numbers[N: Numeric](xs: N*): DynamoArray = if (xs.isEmpty) Empty else PureN(xs.toList.map(_.toString))
  def byteBuffers(xs: ju.List[ByteBuffer]): DynamoArray = if (xs.isEmpty) Empty else StrictB(xs)
  def byteBuffers(xs: ByteBuffer*): DynamoArray = if (xs.isEmpty) Empty else PureB(xs.toList)

  val empty: DynamoArray = Empty

  private[scanamo] val EmptyList: DynamoArray = Pure(Nil)
}
