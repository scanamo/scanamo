package org.scanamo

import cats.Monoid
import cats.kernel.Eq
import cats.instances.string._
import cats.instances.list._
import cats.syntax.eq._
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.nio.ByteBuffer

sealed abstract class DynamoValue extends Product with Serializable { self =>
  import DynamoValue._

  def toAttributeValue: AttributeValue = self match {
    case DynNull        => Null
    case DynBool(true)  => True
    case DynBool(false) => False
    case DynNum(n)      => new AttributeValue().withN(n.toString)
    case DynString(s)   => new AttributeValue().withS(s)
    case DynByte(b)     => new AttributeValue().withB(b)
    case DynMap(as) =>
      val m = new java.util.HashMap[String, AttributeValue](as.size)
      as foreach { case (k, a) => m.put(k, a.toAttributeValue) }
      new AttributeValue().withM(m)
    case DynArray(as)       => collection[AttributeValue](as.map(_.toAttributeValue), _ withL _)
    case DynNumArray(ns)    => collection[String](ns.map(_.toString), _ withNS _)
    case DynStringArray(ss) => collection[String](ss, _ withSS _)
    case DynByteArray(bs)   => collection[ByteBuffer](bs, _ withBS _)
  }

  final def isNil: Boolean = self match {
    case DynNull => true
    case _       => false
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

  final def asList: Option[List[DynamoValue]] = self match {
    case DynArray(as) => Some(as)
    case _            => None
  }

  final def asObject: Option[Map[String, DynamoValue]] = self match {
    case DynMap(as) => Some(as)
    case _          => None
  }

  final def asNumArray: Option[List[String]] = self match {
    case DynNumArray(ns) => Some(ns)
    case _               => None
  }

  final def asStringArray: Option[List[String]] = self match {
    case DynStringArray(ns) => Some(ns)
    case _                  => None
  }

  def <>(that: DynamoValue): DynamoValue = self match {
    case DynNull => that
    case DynArray(as) =>
      that match {
        case DynArray(bs) => DynArray(as ++ bs)
        case DynNull      => self
        case a            => DynArray(as :+ a)
      }
    case a @ DynMap(as) =>
      that match {
        case DynMap(bs)   => DynMap(as ++ bs)
        case DynNull      => self
        case DynArray(bs) => DynArray(a :: bs)
        case b            => DynArray(a :: b :: Nil)
      }
    case a =>
      that match {
        case DynArray(as) => DynArray(a :: as)
        case DynNull      => self
        case b            => DynArray(a :: b :: Nil)
      }
  }

  def ::(that: DynamoValue)(implicit ev: self.type =:= DynArray): DynamoValue =
    DynArray(that :: self.as)

  def :+(that: DynamoValue)(implicit ev: self.type =:= DynArray): DynamoValue =
    DynArray(self.as :+ that)

  def +(that: (String, DynamoValue))(implicit ev: self.type =:= DynMap): DynamoValue =
    DynMap(self.as + that)
}

object DynamoValue {
  private[DynamoValue] val Null = new AttributeValue().withNULL(true)
  private[DynamoValue] val True = new AttributeValue().withBOOL(true)
  private[DynamoValue] val False = new AttributeValue().withBOOL(false)

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
  private[DynamoValue] final case class DynArray(as: List[DynamoValue]) extends DynamoValue
  private[DynamoValue] final case class DynMap(as: Map[String, DynamoValue]) extends DynamoValue
  private[DynamoValue] final case class DynNumArray(ns: List[String]) extends DynamoValue
  private[DynamoValue] final case class DynStringArray(ss: List[String]) extends DynamoValue
  private[DynamoValue] final case class DynByteArray(bs: List[ByteBuffer]) extends DynamoValue

  ////
  // Constructors
  final val nil: DynamoValue = DynNull
  final def boolean(b: Boolean): DynamoValue = DynBool(b)
  final def number[N: Numeric](n: N): DynamoValue = DynNum(n.toString)
  final def string(s: String): DynamoValue = DynString(s)
  final def byteBuffer(b: ByteBuffer): DynamoValue = DynByte(b)
  final def array(as: DynamoValue*): DynamoValue = DynArray(as.toList)
  final def keyStore(as: (String, DynamoValue)*): DynamoValue = DynMap(Map(as: _*))
  final def keyStore(as: Map[String, DynamoValue]): DynamoValue = DynMap(as)
  final def numbers[N: Numeric](ns: N*): DynamoValue = DynNumArray(ns.toList.map(_.toString))
  final def strings(ss: String*): DynamoValue = DynStringArray(ss.toList)
  final def byteBuffers(bs: ByteBuffer*): DynamoValue = DynByteArray(bs.toList)

  final def fromAttributeValue(av: AttributeValue): DynamoValue =
    if (av.isNULL)
      DynNull
    else if (av.isBOOL)
      DynBool(av.getBOOL)
    else if (av.getN ne null)
      DynNum(av.getN)
    else if (av.getS ne null)
      DynString(av.getS)
    else if (av.getB ne null)
      DynByte(av.getB)
    else if (av.getNS ne null)
      DynNumArray(av.getNS.stream.reduce[List[String]](Nil, _ :+ _, _ ++ _))
    else if (av.getBS ne null)
      DynByteArray(av.getBS.stream.reduce[List[ByteBuffer]](Nil, _ :+ _, _ ++ _))
    else if (av.getSS ne null)
      DynStringArray(av.getSS.stream.reduce[List[String]](Nil, _ :+ _, _ ++ _))
    else if (av.getL ne null)
      av.getL.stream
        .reduce[DynamoValue](
          DynNull,
          (acc, av) => acc <> fromAttributeValue(av),
          _ <> _
        )
    else
      DynMap(
        av.getM.entrySet.stream
          .reduce[Map[String, DynamoValue]](
            Map.empty,
            (xs, x) => xs + (x.getKey -> fromAttributeValue(x.getValue)),
            _ ++ _
          )
      )

  ////
  // Typeclass instances
  implicit val monoid: Monoid[DynamoValue] = new Monoid[DynamoValue] {
    final def combine(x: DynamoValue, y: DynamoValue) = x <> y
    final val empty = nil
  }

  implicit val eq: Eq[DynamoValue] = new Eq[DynamoValue] {
    final def eqv(x: DynamoValue, y: DynamoValue): Boolean = (x, y) match {
      case (DynNull, DynNull)           => true
      case (DynNum(x), DynNum(y))       => x == y
      case (DynBool(x), DynBool(y))     => x == y
      case (DynString(x), DynString(y)) => x == y
      case (DynByte(x), DynByte(y))     => x equals y
      case (DynArray(xs), DynArray(ys)) => (xs zip ys).forall { case (x, y) => eqv(x, y) }
      case (DynMap(xs), DynMap(ys)) =>
        xs.keys.toList === ys.keys.toList && (xs.values zip ys.values).forall { case (x, y) => eqv(x, y) }
      case (DynNumArray(xs), DynNumArray(ys))       => xs == ys
      case (DynStringArray(xs), DynStringArray(ys)) => xs == ys
      case (DynByteArray(xs), DynByteArray(ys))     => xs == ys
      case _                                        => false
    }
  }
}
