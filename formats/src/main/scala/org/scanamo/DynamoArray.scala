package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.util.Collection
import java.nio.ByteBuffer
import java.util.stream.Collectors

sealed abstract class DynamoArray extends Product with Serializable { self =>
  import DynamoArray._

  def toAttributeValue: AttributeValue = self match {
    case Strict(xs) => new AttributeValue().withL(xs)
    case StrictS(xs) => new AttributeValue().withSS(xs)
    case StrictN(xs) => new AttributeValue().withNS(xs)
    case StrictB(xs) => new AttributeValue().withBS(xs)
    case Pure(xs) => new AttributeValue().withL(unsafeToCollection(xs, _.toAttributeValue))
    case PureS(xs) => new AttributeValue().withSS(unsafeToCollection(xs, identity[String]))
    case PureN(xs) => new AttributeValue().withNS(unsafeToCollection(xs, identity[String]))
    case PureB(xs) => new AttributeValue().withBS(unsafeToCollection(xs, identity[ByteBuffer]))
    case Concat(xs, ys) => new AttributeValue().withL(unsafeMerge[AttributeValue](xs.apply, ys.apply))
  }

  def apply: Collection[AttributeValue] = self match {
    case Strict(xs) => xs
    case StrictS(xs) => xs.stream.map[AttributeValue](new AttributeValue().withS(_)).collect(Collectors.toList())
    case StrictN(xs) => xs.stream.map[AttributeValue](new AttributeValue().withN(_)).collect(Collectors.toList())
    case StrictB(xs) => xs.stream.map[AttributeValue](new AttributeValue().withB(_)).collect(Collectors.toList())
    case Pure(xs) => unsafeToCollection[DynamoValue, AttributeValue](xs, _.toAttributeValue)
    case PureS(xs) => unsafeToCollection(xs, new AttributeValue().withS(_))
    case PureN(xs) => unsafeToCollection(xs, new AttributeValue().withN(_))
    case PureB(xs) => unsafeToCollection(xs, new AttributeValue().withB(_))
    case Concat(xs, ys) => unsafeMerge(xs.apply, ys.apply)
  }

  // def toDynamoValue: DynamoValue = DynamoValue.fromDynamoArray(self)

  def isAttributeValueArray: Boolean = self match {
    case _: Strict | _: Pure => true
    case Concat(xs, ys) => xs.isAttributeValueArray && ys.isAttributeValueArray
    case _ => false
  }

  def isStringArray: Boolean = self match {
    case _: StrictS | _: PureS => true
    case Concat(xs, ys) => xs.isStringArray && ys.isStringArray
    case _ => false
  }

  def isNumArray: Boolean = self match {
    case _: StrictN | _: PureN => true
    case Concat(xs, ys) => xs.isNumArray && ys.isNumArray
    case _ => false
  }

  def isByteBufferArray: Boolean = self match {
    case _: StrictB | _: PureB => true
    case Concat(xs, ys) => xs.isByteBufferArray && ys.isByteBufferArray
    case _ => false
  }

  def <>(that: DynamoArray): DynamoArray = DynamoArray.Concat(self, that)
}

object DynamoArray {
  private[DynamoArray] final case class Strict(xs: Collection[AttributeValue]) extends DynamoArray
  private[DynamoArray] final case class StrictS(xs: Collection[String]) extends DynamoArray
  private[DynamoArray] final case class StrictN(xs: Collection[String]) extends DynamoArray
  private[DynamoArray] final case class StrictB(xs: Collection[ByteBuffer]) extends DynamoArray
  private[DynamoArray] final case class Pure(xs: List[DynamoValue]) extends DynamoArray
  private[DynamoArray] final case class PureS(xs: List[String]) extends DynamoArray
  private[DynamoArray] final case class PureN(xs: List[String]) extends DynamoArray
  private[DynamoArray] final case class PureB(xs: List[ByteBuffer]) extends DynamoArray
  private[DynamoArray] final case class Concat(xs: DynamoArray, ys: DynamoArray) extends DynamoArray

  private[DynamoArray] def unsafeMerge[A](xs: Collection[A], ys: Collection[A]): Collection[A] = ???

  private[DynamoArray] def unsafeToCollection[A, B](xs: List[A], f: A => B): Collection[B] = ???

  def apply(xs: Collection[AttributeValue]): DynamoArray = Strict(xs)
  def strings(xs: Collection[String]): DynamoArray = StrictS(xs)
  def numbers(xs: Collection[String]): DynamoArray = StrictN(xs)
  def byteBuffers(xs: Collection[ByteBuffer]): DynamoArray = StrictB(xs)
}