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

import java.nio.ByteBuffer
import java.time.format.{ DateTimeFormatter, DateTimeParseException }
import java.time.{ Instant, OffsetDateTime, ZonedDateTime }
import java.util.UUID

import cats.instances.either._
import cats.instances.list._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.traverse._
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

/**
  * Type class for defining serialisation to and from * DynamoDB's `AttributeValue`.
  */
@implicitNotFound(
  "There is no format for ${T}, you may have to do one of the following:\n" +
    "  1- enable automatic derivation:\n" +
    "      ```\n" +
    "        import org.scanamo.generic.auto._\n" +
    "      ```\n" +
    "  2- enable semi-automatic derivation:\n" +
    "      ```\n" +
    "        import org.scanamo.generic.semiauto._\n" +
    "        implicit val format${T}: DynamoFormat[${T}] = deriveDynamoFormat[${T}]\n" +
    "      ```\n" +
    "  3- or write your own custom format:\n" +
    "      ```\n" +
    "        implicit val format${T}: DynamoFormat[${T}] =\n" +
    "          new DynamoFormat[${T}] {\n" +
    "            ...\n" +
    "          }\n" +
    "      ```"
)
trait DynamoFormat[T] {
  def read(av: DynamoValue): Either[DynamoReadError, T]
  def read(av: AttributeValue): Either[DynamoReadError, T] = read(DynamoValue.fromAttributeValue(av))
  def write(t: T): DynamoValue

  def iso[U](r: T => U, w: U => T): DynamoFormat[U] = DynamoFormat.iso(r, w)(this)
  def xmap[U](r: T => Either[DynamoReadError, U], w: U => T): DynamoFormat[U] = DynamoFormat.xmap(r, w)(this)
  def coercedXmap[U](read: T => U, write: U => T): DynamoFormat[U] =
    DynamoFormat.coercedXmap(read, write)(this, implicitly)
}

object DynamoFormat extends PlatformSpecificFormat {
  def apply[T](implicit D: DynamoFormat[T]): DynamoFormat[T] = D

  def build[T](r: DynamoValue => Either[DynamoReadError, T], w: T => DynamoValue): DynamoFormat[T] =
    new DynamoFormat[T] {
      def read(av: DynamoValue): Either[DynamoReadError, T] = r(av)
      def write(t: T): DynamoValue = w(t)
    }

  /**
    * DynamoFormats for object-like structures
    *
    * @note All data types used as the carrier type in [[Table]] operations
    * should derive an instance from this class
    */
  trait ObjectFormat[T] extends DynamoFormat[T] {
    def readObject(o: DynamoObject): Either[DynamoReadError, T]
    def writeObject(t: T): DynamoObject

    final def read(dv: DynamoValue): Either[DynamoReadError, T] =
      dv.asObject.fold[Either[DynamoReadError, T]](Left(NoPropertyOfType("M", dv)))(readObject)

    final def write(t: T): DynamoValue =
      writeObject(t).toDynamoValue
  }

  object ObjectFormat {
    def apply[T](implicit T: ObjectFormat[T]): ObjectFormat[T] = T

    def build[T](r: DynamoObject => Either[DynamoReadError, T], w: T => DynamoObject): ObjectFormat[T] =
      new ObjectFormat[T] {
        def readObject(o: DynamoObject): Either[DynamoReadError, T] = r(o)
        def writeObject(t: T): DynamoObject = w(t)
      }
  }

  private[scanamo] def coerce[A, B, T >: Null <: Throwable: ClassTag](f: A => B): A => Either[DynamoReadError, B] =
    a => Either.catchOnly[T](f(a)).leftMap(TypeCoercionError(_))

  /**
    * Returns a [[DynamoFormat]] for the case where `A` and `B` are isomorphic,
    * i.e. an `A` can always be converted to a `B` and vice versa.
    *
    * If there are some values of `B` that have no corresponding value in `A`,
    * use [[DynamoFormat.xmap]] or [[DynamoFormat.coercedXmap]].
    */
  def iso[A, B](r: B => A, w: A => B)(implicit f: DynamoFormat[B]): DynamoFormat[A] =
    new DynamoFormat[A] {
      final def read(item: DynamoValue): Either[DynamoReadError, A] = f.read(item).map(r)
      final def write(t: A): DynamoValue = f.write(w(t))
    }

  /**
    * Returns a [[DynamoFormat]] for the case where `A` and `B` form an epimorphism,
    * i.e. an `A` can always be converted to a `B` but the opposite is not necessarily true.
    */
  def xmap[A, B](r: B => Either[DynamoReadError, A], w: A => B)(implicit f: DynamoFormat[B]): DynamoFormat[A] =
    new DynamoFormat[A] {
      final def read(item: DynamoValue): Either[DynamoReadError, A] = f.read(item).flatMap(r)
      final def write(t: A): DynamoValue = f.write(w(t))
    }

  /**
    * Returns a [[DynamoFormat]] for the case where `A` can always be converted `B`,
    * with `write`, but `read` may throw an exception for some value of `B`
    */
  def coercedXmap[A, B: DynamoFormat, T >: Null <: Throwable: ClassTag](read: B => A, write: A => B): DynamoFormat[A] =
    xmap(coerce[B, A, T](read), write)

  private def coerceNumber[N: Numeric](f: String => N): String => Either[DynamoReadError, N] =
    DynamoFormat.coerce[String, N, NumberFormatException](f)

  private def coerceByteBuffer[B](f: ByteBuffer => B): ByteBuffer => Either[DynamoReadError, B] =
    DynamoFormat.coerce[ByteBuffer, B, IllegalArgumentException](f)

  private def attribute[T](
    decode: DynamoValue => Option[T],
    encode: T => DynamoValue,
    propertyType: String
  ): DynamoFormat[T] =
    new DynamoFormat[T] {
      final def read(av: DynamoValue): Either[DynamoReadError, T] =
        Either.fromOption(decode(av), NoPropertyOfType(propertyType, av))
      final def write(t: T): DynamoValue = encode(t)
    }

  implicit val stringFormat: DynamoFormat[String] = new DynamoFormat[String] {
    final def read(av: DynamoValue): Either[DynamoReadError, String] =
      if (av.isNull)
        Right("")
      else
        av.asString.fold[Either[DynamoReadError, String]](Left(NoPropertyOfType("S", av)))(Right(_))

    final def write(s: String): DynamoValue =
      s match {
        case "" => DynamoValue.nil
        case _  => DynamoValue.fromString(s)
      }
  }

  implicit val booleanFormat: DynamoFormat[Boolean] = attribute(_.asBoolean, DynamoValue.fromBoolean, "BOOL")

  private def numFormat[N: Numeric](f: String => N): DynamoFormat[N] =
    new DynamoFormat[N] {
      final def read(av: DynamoValue): Either[DynamoReadError, N] =
        for {
          ns <- Either.fromOption(av.asNumber, NoPropertyOfType("N", av))
          transform = coerceNumber(f)
          n <- transform(ns)
        } yield n

      final def write(n: N): DynamoValue = DynamoValue.fromNumber(n)
    }

  implicit val longFormat: DynamoFormat[Long] = numFormat(_.toLong)

  implicit val intFormat: DynamoFormat[Int] = numFormat(_.toInt)

  implicit val floatFormat: DynamoFormat[Float] = numFormat(_.toFloat)

  implicit val doubleFormat: DynamoFormat[Double] = numFormat(_.toDouble)

  implicit val bigDecimalFormat: DynamoFormat[BigDecimal] = numFormat(BigDecimal(_))

  implicit val shortFormat: DynamoFormat[Short] = numFormat(_.toShort)

  // Thrift and therefore Scanamo-Scrooge provides a byte and binary types backed by byte and byte[].
  implicit val byteFormat: DynamoFormat[Byte] = numFormat(_.toByte)

  // Since DynamoValue includes a ByteBuffer instance, creating byteArray format backed by ByteBuffer
  implicit val byteBufferFormat: DynamoFormat[ByteBuffer] = attribute(_.asByteBuffer, DynamoValue.fromByteBuffer, "B")

  implicit val byteArrayFormat: DynamoFormat[Array[Byte]] =
    DynamoFormat.xmap(coerceByteBuffer(_.array), ByteBuffer.wrap)(byteBufferFormat)

  implicit val uuidFormat: DynamoFormat[UUID] =
    DynamoFormat.coercedXmap[UUID, String, IllegalArgumentException](UUID.fromString, _.toString)

  implicit val javaListFormat: DynamoFormat[List[DynamoValue]] =
    attribute(
      dv =>
        if (dv.isNull) Some(List.empty)
        else dv.asArray.flatMap(_.asArray),
      l => DynamoValue.fromValues(l),
      "L"
    )

  implicit def listFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[List[T]] =
    DynamoFormat.xmap[List[T], List[DynamoValue]](_.traverse(f.read), _.map(f.write))(javaListFormat)

  implicit def seqFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Seq[T]] =
    DynamoFormat.xmap[Seq[T], List[T]](l => Right(l.toSeq), _.toList)

  implicit def vectorFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Vector[T]] =
    DynamoFormat.xmap[Vector[T], List[DynamoValue]](_.toVector.traverse(f.read), _.map(f.write).toList)(javaListFormat)

  implicit def arrayFormat[T: ClassTag](implicit f: DynamoFormat[T]): DynamoFormat[Array[T]] =
    DynamoFormat.xmap[Array[T], List[DynamoValue]](
      _.traverse(f.read).map(_.toArray),
      _.map(f.write).toList
    )(javaListFormat)

  private def numSetFormat[T: Numeric](r: String => Either[DynamoReadError, T]): DynamoFormat[Set[T]] =
    new DynamoFormat[Set[T]] {
      final def read(av: DynamoValue): Either[DynamoReadError, Set[T]] =
        if (av.isNull)
          Right(Set.empty[T])
        else
          for {
            ns <- Either.fromOption(av.asArray.flatMap(_.asNumericArray), NoPropertyOfType("NS", av))
            set <- ns.traverse(r)
          } yield set.toSet

      // Set types cannot be empty
      final def write(t: Set[T]): DynamoValue =
        if (t.isEmpty)
          DynamoValue.nil
        else
          DynamoValue.fromNumbers(t)
    }

  implicit val intSetFormat: DynamoFormat[Set[Int]] = numSetFormat(coerceNumber(_.toInt))

  implicit val longSetFormat: DynamoFormat[Set[Long]] = numSetFormat(coerceNumber(_.toLong))

  implicit val floatSetFormat: DynamoFormat[Set[Float]] = numSetFormat(coerceNumber(_.toFloat))

  implicit val doubleSetFormat: DynamoFormat[Set[Double]] = numSetFormat(coerceNumber(_.toDouble))

  implicit val BigDecimalSetFormat: DynamoFormat[Set[BigDecimal]] = numSetFormat(coerceNumber(BigDecimal(_)))

  implicit val stringSetFormat: DynamoFormat[Set[String]] =
    new DynamoFormat[Set[String]] {
      final def read(av: DynamoValue): Either[DynamoReadError, Set[String]] =
        if (av.isNull)
          Right(Set.empty)
        else
          Either.fromOption(av.asArray.flatMap(_.asStringArray).map(_.toSet), NoPropertyOfType("SS", av))

      // Set types cannot be empty
      final def write(t: Set[String]): DynamoValue =
        if (t.isEmpty)
          DynamoValue.nil
        else
          DynamoValue.fromStrings(t)
    }

  implicit def genericSet[T: DynamoFormat]: DynamoFormat[Set[T]] = DynamoFormat.iso[Set[T], List[T]](_.toSet, _.toList)

  private val javaMapFormat: DynamoFormat[DynamoObject] =
    attribute(_.asObject, DynamoValue.fromDynamoObject, "M")

  implicit def mapFormat[V](implicit f: DynamoFormat[V]): DynamoFormat[Map[String, V]] =
    xmap[Map[String, V], DynamoObject](_.toMap[V], m => DynamoObject(m.toSeq: _*))(javaMapFormat)

  implicit def optionFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Option[T]] =
    new DynamoFormat[Option[T]] {
      final def read(av: DynamoValue): Either[DynamoReadError, Option[T]] =
        if (av.isNull)
          Right(None)
        else
          f.read(av).map(Some(_))

      final def write(t: Option[T]): DynamoValue = t.fold(DynamoValue.nil)(f.write)
    }

  /**
    * This ensures that if, for instance, you specify an update with Some(5) rather
    * than making the type of `Option` explicit, it doesn't fall back to auto-derivation
    */
  implicit def someFormat[T](implicit f: DynamoFormat[T]): DynamoFormat[Some[T]] =
    new DynamoFormat[Some[T]] {
      def read(av: DynamoValue): Either[DynamoReadError, Some[T]] =
        Option(av).map(f.read(_).map(Some(_))).getOrElse(Left[DynamoReadError, Some[T]](MissingProperty))

      def write(t: Some[T]): DynamoValue = f.write(t.get)
    }

  /**  Format for dealing with points in time stored as the number of milliseconds since Epoch.
    */
  implicit val instantAsLongFormat: DynamoFormat[Instant] =
    DynamoFormat.coercedXmap[Instant, Long, ArithmeticException](x => Instant.ofEpochMilli(x), x => x.toEpochMilli)

  /**  Format for dealing with date-times with an offset from UTC.
    */
  implicit val offsetDateTimeFormat: DynamoFormat[OffsetDateTime] =
    DynamoFormat.coercedXmap[OffsetDateTime, String, DateTimeParseException](
      OffsetDateTime.parse,
      _.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    )

  /**  Format for dealing with date-times with a time zone in the ISO-8601 calendar system.
    */
  implicit val zonedDateTimeFormat: DynamoFormat[ZonedDateTime] =
    DynamoFormat.coercedXmap[ZonedDateTime, String, DateTimeParseException](
      ZonedDateTime.parse,
      _.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    )
}
