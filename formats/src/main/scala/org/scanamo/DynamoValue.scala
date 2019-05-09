package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.nio.ByteBuffer
import org.scanamo.error.DynamoReadError

sealed abstract class DynamoValue extends Product with Serializable { self =>
  import DynamoValue._

  def toAttributeValue: AttributeValue = self match {
    case DynNull        => Null
    case DynBool(true)  => True
    case DynBool(false) => False
    case DynNum(n)      => new AttributeValue().withN(n.toString)
    case DynString(s)   => new AttributeValue().withS(s)
    case DynByte(b)     => new AttributeValue().withB(b)
    case DynObject(as)  => as.toAttributeValue
    case DynArray(as)   => as.toAttributeValue
  }

  final def isNull: Boolean = self match {
    case DynNull => true
    case _       => false
  }

  final def isBoolean: Boolean = self match {
    case _: DynBool => true
    case _          => false
  }

  final def isNumber: Boolean = self match {
    case _: DynNum => true
    case _         => false
  }

  final def isString: Boolean = self match {
    case _: DynString => true
    case _            => false
  }

  final def isByteBuffer: Boolean = self match {
    case _: DynByte => true
    case _          => false
  }

  final def isObject: Boolean = self match {
    case _: DynObject => true
    case _            => false
  }

  final def isArray: Boolean = self match {
    case DynArray(x) => x.isAttributeValueArray
    case _           => false
  }

  final def isNumArray: Boolean = self match {
    case DynArray(x) => x.isNumArray
    case _           => false
  }

  final def isStringArray: Boolean = self match {
    case DynArray(x) => x.isStringArray
    case _           => false
  }

  final def isByteButterArray: Boolean = self match {
    case DynArray(x) => x.isByteBufferArray
    case _           => false
  }

  final def asNull: Option[Unit] = self match {
    case DynNull => Some(())
    case _       => None
  }

  final def asBoolean: Option[Boolean] = self match {
    case DynBool(b) => Some(b)
    case _          => None
  }

  final def asString: Option[String] = self match {
    case DynString(s) => Some(s)
    case _            => None
  }

  final def asNumber: Option[String] = self match {
    case DynNum(n) => Some(n)
    case _         => None
  }

  final def asByteBuffer: Option[ByteBuffer] = self match {
    case DynByte(b) => Some(b)
    case _          => None
  }

  final def asArray: Option[DynamoArray] = self match {
    case DynArray(as) => Some(as)
    case _            => None
  }

  final def asObject: Option[DynamoObject] = self match {
    case DynObject(as) => Some(as)
    case _             => None
  }

  final def as[A](implicit A: DynamoFormat[A]): Either[DynamoReadError, A] = A.read(self)

  final def withNull(f: => DynamoValue): DynamoValue = self match {
    case DynNull => f
    case _       => self
  }

  final def withBoolean(f: Boolean => DynamoValue): DynamoValue = self match {
    case DynBool(b) => f(b)
    case _          => self
  }

  final def withString(f: String => DynamoValue): DynamoValue = self match {
    case DynString(s) => f(s)
    case _            => self
  }

  final def withNumber(f: String => DynamoValue): DynamoValue = self match {
    case DynNum(n) => f(n)
    case _         => self
  }

  final def withByteBuffer(f: ByteBuffer => DynamoValue): DynamoValue = self match {
    case DynByte(b) => f(b)
    case _          => self
  }

  final def withArray(f: DynamoArray => DynamoValue): DynamoValue = self match {
    case DynArray(as) => f(as)
    case _            => self
  }

  final def withObject(f: DynamoObject => DynamoValue): DynamoValue = self match {
    case DynObject(as) => f(as)
    case _             => self
  }
}

object DynamoValue {
  private[scanamo] val Null: AttributeValue = new AttributeValue().withNULL(true)
  private[scanamo] val True: AttributeValue = new AttributeValue().withBOOL(true)
  private[scanamo] val False: AttributeValue = new AttributeValue().withBOOL(false)
  private[scanamo] val EmptyList: AttributeValue = new AttributeValue().withL()

  private[DynamoValue] def collection[A](
    xs: Iterable[A],
    f: (AttributeValue, java.util.Collection[A]) => AttributeValue
  ) = {
    val c = new java.util.Vector[A](xs.size, 0)
    xs.zipWithIndex foreach { case (x, i) => c.add(i, x) }
    f(new AttributeValue(), c)
  }

  private[DynamoValue] case object DynNull extends DynamoValue
  private[DynamoValue] final case class DynBool(b: Boolean) extends DynamoValue
  private[DynamoValue] final case class DynNum(n: String) extends DynamoValue
  private[DynamoValue] final case class DynString(s: String) extends DynamoValue
  private[DynamoValue] final case class DynByte(b: ByteBuffer) extends DynamoValue
  private[DynamoValue] final case class DynArray(as: DynamoArray) extends DynamoValue
  private[DynamoValue] final case class DynObject(as: DynamoObject) extends DynamoValue

  ////
  // Constructors
  final val nil: DynamoValue = DynNull
  final def boolean(b: Boolean): DynamoValue = DynBool(b)
  final def number[N: Numeric](n: N): DynamoValue = DynNum(n.toString)
  final def string(s: String): DynamoValue = DynString(s)
  final def byteBuffer(b: ByteBuffer): DynamoValue = DynByte(b)
  final def array(as: DynamoValue*): DynamoValue = DynArray(DynamoArray(as: _*))
  final def map(as: (String, DynamoValue)*): DynamoValue = DynObject(DynamoObject(as.toMap))
  final def map(as: Map[String, DynamoValue]): DynamoValue = DynObject(DynamoObject(as))
  final def numbers[N: Numeric](ns: N*): DynamoValue = DynArray(DynamoArray.numbers(ns: _*))
  final def strings(ss: String*): DynamoValue = DynArray(DynamoArray.strings(ss: _*))
  final def byteBuffers(bs: ByteBuffer*): DynamoValue = DynArray(DynamoArray.byteBuffers(bs: _*))

  private[scanamo] def unsafeNumber(n: String): DynamoValue = DynNum(n)

  final def fromAttributeValue(av: AttributeValue): DynamoValue =
    if ((av eq null) || av.isNULL)
      DynNull
    else if (av.getBOOL ne null)
      DynBool(av.getBOOL)
    else if (av.getN ne null)
      DynNum(av.getN)
    else if (av.getS ne null)
      DynString(av.getS)
    else if (av.getB ne null)
      DynByte(av.getB)
    else if (av.getNS ne null)
      DynArray(DynamoArray.numbers(av.getNS))
    else if (av.getBS ne null)
      DynArray(DynamoArray.byteBuffers(av.getBS))
    else if (av.getSS ne null)
      DynArray(DynamoArray.strings(av.getSS))
    else if (av.getL ne null)
      DynArray(DynamoArray(av.getL))
    else
      DynObject(DynamoObject(av.getM))

  def fromDynamoObject(xs: DynamoObject): DynamoValue = DynObject(xs)

  def fromDynamoArray(xs: DynamoArray): DynamoValue = DynArray(xs)
}
