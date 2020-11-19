/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo

import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import cats.syntax.apply._
import java.nio.ByteBuffer
import java.util.stream.Collectors
import java.{ util => ju }
import software.amazon.awssdk.core.SdkBytes

/** A `DynamoArray` is a pure representation of an array of `AttributeValue`s
  */
sealed abstract class DynamoArray extends Product with Serializable { self =>
  import DynamoArray._

  protected def toList: List[DynamoValue]

  /** Gets the `i`th value in the array
    */
  final def apply(i: Int): Option[DynamoValue] =
    self match {
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

  /** Gets the size of the array
    */
  final def size: Int =
    self match {
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

  /** Checks if the array is empty
    */
  final def isEmpty: Boolean =
    self match {
      case Empty => true
      case _     => false
    }

  /** Chekcs if the array is not empty
    */
  final def nonEmpty: Boolean = !isEmpty

  /** Chekcs if the arra contains a certain `x`
    */
  final def contains(x: DynamoValue): Boolean =
    self match {
      case Empty          => false
      case Strict(xs)     => xs.contains(x.toAttributeValue)
      case Pure(xs)       => xs.contains(x)
      case StrictS(xs)    => x.asString.fold(false)(xs.contains(_))
      case PureS(xs)      => x.asString.fold(false)(xs.contains(_))
      case StrictN(xs)    => x.asNumber.fold(false)(xs.contains(_))
      case PureN(xs)      => x.asNumber.fold(false)(xs.contains(_))
      case StrictB(xs)    => x.asByteBuffer.fold(false)(xs.contains(_))
      case PureB(xs)      => x.asByteBuffer.fold(false)(xs.contains(_))
      case Concat(xs, ys) => xs.contains(x) || ys.contains(x)
    }

  /** Make an AWS SDK value out of this array
    */
  // TODO mark as private?
  final def toAttributeValue: AttributeValue =
    self match {
      case Empty       => DynamoValue.EmptyList
      case Strict(xs)  => AttributeValue.builder.l(xs).build
      case StrictS(xs) => AttributeValue.builder.ss(xs).build
      case StrictN(xs) => AttributeValue.builder.ns(xs).build
      case StrictB(xs) =>
        AttributeValue.builder
          .bs(xs.stream.map[SdkBytes](SdkBytes.fromByteBuffer(_)).collect(Collectors.toList()))
          .build
      case Pure(xs)  => AttributeValue.builder.l(unsafeToList[DynamoValue, AttributeValue](xs, _.toAttributeValue)).build
      case PureS(xs) => AttributeValue.builder.ss(unsafeToList(xs, identity[String])).build
      case PureN(xs) => AttributeValue.builder.ns(unsafeToList(xs, identity[String])).build
      case PureB(xs) => AttributeValue.builder.bs(unsafeToList(xs, SdkBytes.fromByteBuffer(_))).build
      case Concat(xs, ys) =>
        AttributeValue.builder.l(unsafeMerge[AttributeValue](xs.toJavaCollection, ys.toJavaCollection)).build
    }

  // TODO mark as private?
  final def toJavaCollection: ju.List[AttributeValue] =
    self match {
      case Empty       => new java.util.LinkedList[AttributeValue]()
      case Strict(xs)  => xs
      case StrictS(xs) => xs.stream.map[AttributeValue](AttributeValue.builder.s(_).build).collect(Collectors.toList())
      case StrictN(xs) => xs.stream.map[AttributeValue](AttributeValue.builder.n(_).build).collect(Collectors.toList())
      case StrictB(xs) =>
        xs.stream
          .map[AttributeValue](b => AttributeValue.builder.b(SdkBytes.fromByteBuffer(b)).build)
          .collect(Collectors.toList())
      case Pure(xs)  => unsafeToList[DynamoValue, AttributeValue](xs, _.toAttributeValue)
      case PureS(xs) => unsafeToList[String, AttributeValue](xs, AttributeValue.builder.s(_).build)
      case PureN(xs) => unsafeToList[String, AttributeValue](xs, AttributeValue.builder.n(_).build)
      case PureB(xs) =>
        unsafeToList[ByteBuffer, AttributeValue](xs, b => AttributeValue.builder.b(SdkBytes.fromByteBuffer(b)).build)
      case Concat(xs, ys) => unsafeMerge(xs.toJavaCollection, ys.toJavaCollection)
    }

  /** Make a value out of this array
    */
  final def toDynamoValue: DynamoValue = DynamoValue.fromDynamoArray(self)

  /** Checks if the array is made of values
    */
  final def isValueArray: Boolean =
    self match {
      case _: Strict | _: Pure => true
      case Concat(xs, ys)      => xs.isValueArray && ys.isValueArray
      case _                   => false
    }

  /** Checks if the array is made of strings
    */
  final def isStringArray: Boolean =
    self match {
      case _: StrictS | _: PureS => true
      case Concat(xs, ys)        => xs.isStringArray && ys.isStringArray
      case _                     => false
    }

  /** Checks if the array is made of numeric values
    */
  final def isNumericArray: Boolean =
    self match {
      case _: StrictN | _: PureN => true
      case Concat(xs, ys)        => xs.isNumericArray && ys.isNumericArray
      case _                     => false
    }

  /** Checks if the array is made of byte buffers
    */
  final def isByteBufferArray: Boolean =
    self match {
      case _: StrictB | _: PureB => true
      case Concat(xs, ys)        => xs.isByteBufferArray && ys.isByteBufferArray
      case _                     => false
    }

  /** Turns into a list of values, if applies
    */
  final def asArray: Option[List[DynamoValue]] =
    self match {
      case Empty => Some(List.empty)
      case Strict(xs0) =>
        Some(
          xs0.stream.reduce[List[DynamoValue]](
            Nil,
            (xs, x) => xs :+ DynamoValue.fromAttributeValue(x),
            _ ++ _
          )
        )
      case Pure(xs)       => Some(xs)
      case Concat(xs, ys) => (xs.asArray, ys.asArray).mapN(_ ++ _)
      case StrictS(xs) =>
        Some(
          xs.stream.map[DynamoValue](DynamoValue.fromString(_)).reduce[List[DynamoValue]](Nil, _ :+ _, _ ++ _).reverse
        )
      case PureS(xs) => Some(xs.map(DynamoValue.fromString))
      case StrictN(xs) =>
        Some(
          xs.stream
            .map[DynamoValue](DynamoValue.unsafeFromNumber(_))
            .reduce[List[DynamoValue]](Nil, _ :+ _, _ ++ _)
            .reverse
        )
      case PureN(xs) => Some(xs.map(DynamoValue.unsafeFromNumber))
      case StrictB(xs) =>
        Some(
          xs.stream
            .map[DynamoValue](DynamoValue.fromByteBuffer(_))
            .reduce[List[DynamoValue]](Nil, _ :+ _, _ ++ _)
            .reverse
        )
      case PureB(xs) => Some(xs.map(DynamoValue.fromByteBuffer))
    }

  /** Turns into a list of strings, if applies
    */
  final def asStringArray: Option[List[String]] =
    self match {
      case Empty          => Some(List.empty)
      case StrictS(xs)    => Some(xs.stream.reduce[List[String]](Nil, _ :+ _, _ ++ _))
      case PureS(xs)      => Some(xs)
      case Concat(xs, ys) => (xs.asStringArray, ys.asStringArray).mapN(_ ++ _)
      case _              => None
    }

  /** Turns into a list of numeric values, if applies
    */
  final def asNumericArray: Option[List[String]] =
    self match {
      case Empty          => Some(List.empty)
      case StrictN(xs)    => Some(xs.stream.reduce[List[String]](Nil, _ :+ _, _ ++ _))
      case PureN(xs)      => Some(xs)
      case Concat(xs, ys) => (xs.asNumericArray, ys.asNumericArray).mapN(_ ++ _)
      case _              => None
    }

  /** Turns into a list of byte buffers, if applies
    */
  final def asByteBufferArray: Option[List[ByteBuffer]] =
    self match {
      case Empty          => Some(List.empty)
      case StrictB(xs)    => Some(xs.stream.reduce[List[ByteBuffer]](Nil, _ :+ _, _ ++ _))
      case PureB(xs)      => Some(xs)
      case Concat(xs, ys) => (xs.asByteBufferArray, ys.asByteBufferArray).mapN(_ ++ _)
      case _              => None
    }

  /** Concatenatres two arrays
    */
  final def concat(that: DynamoArray): DynamoArray =
    if (self.isEmpty) that
    else if (that.isEmpty) self
    else Concat(self, that)

  /** Operator alias for `concat`
    */
  final def <>(that: DynamoArray): DynamoArray = self concat that

  final override def equals(that: Any): Boolean =
    that.isInstanceOf[DynamoArray] && (toList == that.asInstanceOf[DynamoArray].toList)

  final override def hashCode(): Int = toList.hashCode
}

object DynamoArray {
  private[DynamoArray] case object Empty extends DynamoArray {
    final def toList: List[DynamoValue] = Nil
  }
  final private[DynamoArray] case class Strict(xs: ju.List[AttributeValue]) extends DynamoArray {
    final def toList: List[DynamoValue] = unsafeToScalaList(xs, DynamoValue.fromAttributeValue)
  }
  final private[DynamoArray] case class StrictS(xs: ju.List[String]) extends DynamoArray {
    final def toList: List[DynamoValue] = unsafeToScalaList(xs, DynamoValue.fromString)
  }
  final private[DynamoArray] case class StrictN(xs: ju.List[String]) extends DynamoArray {
    final def toList: List[DynamoValue] = unsafeToScalaList(xs, DynamoValue.unsafeFromNumber)
  }
  final private[DynamoArray] case class StrictB(xs: ju.List[ByteBuffer]) extends DynamoArray {
    final def toList: List[DynamoValue] = unsafeToScalaList(xs, DynamoValue.fromByteBuffer)
  }
  final private[DynamoArray] case class Pure(xs: List[DynamoValue]) extends DynamoArray {
    final def toList: List[DynamoValue] = xs
  }
  final private[DynamoArray] case class PureS(xs: List[String]) extends DynamoArray {
    final def toList: List[DynamoValue] = xs.map(DynamoValue.fromString)
  }
  final private[DynamoArray] case class PureN(xs: List[String]) extends DynamoArray {
    final def toList: List[DynamoValue] = xs.map(DynamoValue.unsafeFromNumber)
  }
  final private[DynamoArray] case class PureB(xs: List[ByteBuffer]) extends DynamoArray {
    final def toList: List[DynamoValue] = xs.map(DynamoValue.fromByteBuffer)
  }
  final private[DynamoArray] case class Concat(xs: DynamoArray, ys: DynamoArray) extends DynamoArray {
    final def toList: List[DynamoValue] = xs.toList ++ ys.toList
  }

  private[DynamoArray] def unsafeMerge[A](xs: ju.List[A], ys: ju.List[A]): ju.List[A] =
    java.util.stream.Stream.concat[A](xs.stream, ys.stream).collect(Collectors.toList())

  private[DynamoArray] def unsafeToList[A, B](xs: List[A], f: A => B): ju.List[B] = {
    val l = new java.util.ArrayList[B](xs.size)
    xs foreach { x => l.add(f(x)) }
    l
  }

  private[DynamoArray] def unsafeToScalaList[A, B](xs: ju.List[A], f: A => B): List[B] = {
    val builder = List.newBuilder[B]
    builder.sizeHint(xs.size)
    xs.forEach(x => builder += f(x))
    builder.result()
  }

  /** Make an array out of a list of `AttributeValue`s
    */
  private[scanamo] def apply(xs: ju.List[AttributeValue]): DynamoArray = if (xs.isEmpty) Empty else Strict(xs)

  /** Make an array out of a list of values
    */
  def apply(xs: Iterable[DynamoValue]): DynamoArray = if (xs.isEmpty) Empty else Pure(xs.toList)

  /** Make an array out of a list of strings
    */
  private[scanamo] def strings(xs: ju.List[String]): DynamoArray = if (xs.isEmpty) Empty else StrictS(xs)

  /** Make an array out of a list of strings
    */
  def strings(xs: Iterable[String]): DynamoArray = if (xs.isEmpty) Empty else PureS(xs.toList)

  /** Make an array out of a list of numeric values
    */
  private[scanamo] def unsafeNumbers(xs: ju.List[String]): DynamoArray = if (xs.isEmpty) Empty else StrictN(xs)

  /** Make an array out of a list of numeric values
    */
  def numbers[N: Numeric](xs: Iterable[N]): DynamoArray = if (xs.isEmpty) Empty else PureN(xs.toList.map(_.toString))

  /** Make an array out of a list of byte buffers
    */
  private[scanamo] def byteBuffers(xs: ju.List[ByteBuffer]): DynamoArray = if (xs.isEmpty) Empty else StrictB(xs)

  /** Make an array out of a list of byte buffers
    */
  def byteBuffers(xs: Iterable[ByteBuffer]): DynamoArray = if (xs.isEmpty) Empty else PureB(xs.toList)

  /** The empty array
    */
  val empty: DynamoArray = Empty
}
