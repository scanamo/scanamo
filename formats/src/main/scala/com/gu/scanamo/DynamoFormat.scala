package org.scanamo

import java.util.UUID

import cats.{Applicative, NotNull}
import cats.instances.list._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.traverse._
import org.scanamo.aws.models.AmazonAttribute
import org.scanamo.error._
import simulacrum.typeclass

//import scala.collection.immutable.SortedMap
import scala.reflect.ClassTag

abstract class DynamoFormat[T, Value: AmazonAttribute] {
  def read(av: Value): Either[DynamoReadError, T]
  def write(t: T): Value
  def default: Option[T] = None
}

abstract class DynamoFormatAPI[AttributeValue: AmazonAttribute, Format[T] <: DynamoFormat[T, AttributeValue]]
  extends EnumDynamoFormat {

  protected def attribute[T](
                              decode: AmazonAttribute[AttributeValue] => AttributeValue => T,
                              propertyType: String
                            )(encode: AmazonAttribute[AttributeValue] => AttributeValue => T => AttributeValue): Format[T]


  def iso[A, B](r: B => A)(w: A => B)(implicit f: Format[B]): Format[A]

  def xmap[A, B](
                  r: B => Either[DynamoReadError, A]
                )(w: A => B)(implicit f: Format[B]): Format[A]

  def coercedXmap[A, B, T >: scala.Null <: scala.Throwable](
                                                             read: B => A
                                                           )(write: A => B)(implicit f: Format[B], T: ClassTag[T], NT: NotNull[T]): Format[A] =
    xmap(coerce[B, A, T](read))(write)

  implicit def stringFormat: Format[String] =
    attribute[String](_.getString, "S")(_.setString)

  private def numFormat: Format[String] = attribute[String](_.getNumericString, "N")(_.setNumericString)

  private def coerceNumber[N](f: String => N): String => Either[DynamoReadError, N] =
    coerce[String, N, NumberFormatException](f)

  private def coerce[A, B, T >: scala.Null <: scala.Throwable](f: A => B)(implicit T: ClassTag[T], NT: NotNull[T]): A => Either[DynamoReadError, B] =
    a => Either.catchOnly[T](f(a)).leftMap(TypeCoercionError(_))


  implicit def longFormat: Format[Long] = xmap[Long, String](coerceNumber(_.toLong))(_.toString)(numFormat)

  implicit def intFormat: Format[Int] = xmap[Int, String](coerceNumber(_.toInt))(_.toString)(numFormat)

  implicit val floatFormat: Format[Float] = xmap(coerceNumber(_.toFloat))(_.toString)(numFormat)

  implicit val doubleFormat: Format[Double] = xmap(coerceNumber(_.toDouble))(_.toString)(numFormat)

  implicit val bigDecimalFormat: Format[BigDecimal] = xmap(coerceNumber(BigDecimal(_)))(_.toString)(numFormat)

  implicit val shortFormat: Format[Short] = xmap(coerceNumber(_.toShort))(_.toString)(numFormat)

  implicit def byteFormat: Format[Byte] = xmap(coerceNumber(_.toByte))(_.toString)(numFormat)

  implicit def byteArrayFormat: Format[Array[Byte]] = attribute[Array[Byte]](_.getBytesArray, "B")(_.setBytesArray)

  implicit val uuidFormat: Format[UUID] = coercedXmap[UUID, String, IllegalArgumentException](UUID.fromString)(_.toString)

  val javaListFormat: Format[List[AttributeValue]] = attribute[List[AttributeValue]](_.getList, "L")(_.setList)

  implicit def listFormat[T](implicit r: Format[T], a: Applicative[Either[DynamoReadError, ?]]): Format[List[T]] = {
    xmap[List[T], List[AttributeValue]](_.traverse[Either[DynamoReadError, ?], T](r.read))(_.map(r.write))(javaListFormat)
  }

  implicit def seqFormat[T](implicit f: Format[T], a: Applicative[Either[DynamoReadError, ?]]): Format[Seq[T]] = xmap[Seq[T], List[T]](l => Right(l))(_.toList)(listFormat[T])

  implicit def vectorFormat[T](implicit f: Format[T], a : Applicative[Either[DynamoReadError, ?]]): Format[Vector[T]]

  implicit def arrayFormat[T: ClassTag](implicit f: Format[T], a : Applicative[Either[DynamoReadError, ?]]): Format[Array[T]]

  protected def numSetFormat[T](r: String => Either[DynamoReadError, T])(w: T => String)(implicit a : Applicative[Either[DynamoReadError, ?]]): Format[Set[T]]

  implicit def intSetFormat(implicit a : Applicative[Either[DynamoReadError, ?]]): Format[Set[Int]] = numSetFormat(coerceNumber(_.toInt))(_.toString)(implicitly)

  implicit def longSetFormat(implicit a : Applicative[Either[DynamoReadError, ?]]): Format[Set[Long]] = numSetFormat(coerceNumber(_.toLong))(_.toString)

  implicit def floatSetFormat(implicit a : Applicative[Either[DynamoReadError, ?]]): Format[Set[Float]] = numSetFormat(coerceNumber(_.toFloat))(_.toString)

  implicit def doubleSetFormat(implicit a : Applicative[Either[DynamoReadError, ?]]): Format[Set[Double]] = numSetFormat(coerceNumber(_.toDouble))(_.toString)

  implicit def BigDecimalSetFormat(implicit a : Applicative[Either[DynamoReadError, ?]]): Format[Set[BigDecimal]] = numSetFormat(coerceNumber(BigDecimal(_)))(_.toString)

  implicit def stringSetFormat: Format[Set[String]]

  protected val javaMapFormat: Format[Map[String, AttributeValue]] = attribute[Map[String, AttributeValue]](_.getMap, "M")(_.setMap)

  implicit def mapFormat[V](implicit f: Format[V]): Format[Map[String, V]]

  implicit def optionFormat[T](implicit f: Format[T]): Format[Option[T]]


  implicit def someFormat[T](implicit f: Format[T]): Format[Some[T]]
}

object Foo extends App {

  type AWSV1 = com.amazonaws.services.dynamodbv2.model.AttributeValue

  @typeclass trait DynamoFormatV1[T] extends DynamoFormat[T, AWSV1]
  object DynamoFormatV1 extends DynamoFormatAPI[AWSV1, DynamoFormatV1] {




    override protected def attribute[T](decode: AmazonAttribute[
      AWSV1
      ] => AWSV1 => T,
                                        propertyType: String)(
                                         encode: AmazonAttribute[AWSV1] => AWSV1 => T => AWSV1
                                       ): DynamoFormatV1[T] = {
      new DynamoFormatV1[T] {
        override def read(av: AWSV1): Either[DynamoReadError, T] = {
          val opt = Option(decode(implicitly[AmazonAttribute[AWSV1]])(av))
          Either.fromOption(opt, NoPropertyOfType(propertyType, av))
        }
        override def write(t: T): AWSV1 = {
          val zero = implicitly[AmazonAttribute[AWSV1]]
          encode(zero)(zero.init)(t)
        }
      }
    }

    override def iso[A, B](r: B => A)(w: A => B)(
      implicit f: DynamoFormatV1[B]
    ): DynamoFormatV1[A] = {
      new DynamoFormatV1[A] {
        override def read(item: AWSV1): Either[DynamoReadError, A] = f.read(item).map(r)
        override def write(t: A): AWSV1 = f.write(w(t))
        override val default: Option[A] = f.default.map(r)
      }
    }

    override def xmap[A, B](r: B => Either[DynamoReadError, A])(w: A => B)(
      implicit f: DynamoFormatV1[B]
    ): DynamoFormatV1[A] = {
      new DynamoFormatV1[A] {
        override def read(item: AWSV1): Either[DynamoReadError, A] = f.read(item).flatMap(r)
        override def write(t: A): AWSV1 = f.write(w(t))
      }
    }


    override implicit def vectorFormat[T](
                                           implicit f: DynamoFormatV1[T]
                                           , a : Applicative[Either[DynamoReadError, ?]]
                                         ): DynamoFormatV1[Vector[T]] = {
      xmap[Vector[T], List[AWSV1]](_.toVector.traverse(f.read)(a))(_.toList.map(f.write))(javaListFormat)
    }


    override implicit def arrayFormat[T: ClassTag](
                                                    implicit f: DynamoFormatV1[T]
                                                    , a : Applicative[Either[DynamoReadError, ?]]
                                                  ): DynamoFormatV1[Array[T]] = {
      xmap[Array[T], List[AWSV1]](_.traverse[Either[DynamoReadError, ?], T](f.read).map(_.toArray))(
        _.toArray.map(f.write).toList
      )(javaListFormat)
    }

    override protected def numSetFormat[T](
                                            r: String => Either[DynamoReadError, T]
                                          )(w: T => String)(implicit a : Applicative[Either[DynamoReadError, ?]]): DynamoFormatV1[Set[T]] = {
      new DynamoFormatV1[Set[T]] {

        private val hellper = implicitly[AmazonAttribute[AWSV1]]

        override def read(av: AWSV1): Either[DynamoReadError, Set[T]] = {
          for {
            ns <- Either.fromOption(
              if (hellper.isNull(av)) Some(Nil) else Option(hellper.getNS(av)),
              NoPropertyOfType("NS", av)
            )
            set <- ns.traverse(r)(a)
          } yield set.toSet
        }

        //        Set types cannot be empty
        override def write(t: Set[T]): AWSV1 = {
          val zero = hellper.init
          t.toList match {
            case Nil => hellper.setNull(zero)
            case xs  => hellper.setNS(zero)(xs.map(w))
          }
        }
        override val default: Option[Set[T]] = Some(Set.empty)
      }
    }

    override implicit def stringSetFormat: DynamoFormatV1[Set[String]] = {
      new DynamoFormatV1[Set[String]] {
        private val helper = implicitly[AmazonAttribute[AWSV1]]

        override def read(av: AWSV1): Either[DynamoReadError, Set[String]] =
          for {
            ss <- Either.fromOption(
              if (helper.isNull(av)) Some(Nil) else Option(helper.getSS(av)),
              NoPropertyOfType("SS", av)
            )
          } yield ss.toSet
        // Set types cannot be empty
        override def write(t: Set[String]): AWSV1 = t.toList match {
          case Nil => helper.setNull(helper.init)
          case xs  => helper.setSS(helper.init)(xs)
        }
        override val default: Option[Set[String]] = Some(Set.empty)
      }
    }

    override implicit def mapFormat[V](
                                        implicit f: DynamoFormatV1[V]
                                      ): DynamoFormatV1[Map[String, V]] = {

      xmap[Map[String, V], Map[String, AWSV1]](
        _ => Right(Map.empty)
      )(_.mapValues(i => f.write(i)))(javaMapFormat)

//      xmap[Map[String, V], Map[String, AWSV1]](
//        m => (SortedMap[String, String]() ++ m).traverse(f.read)
//        m => (Map.empty[String, AWSV1] ++ m).tr
//      )(_.mapValues(f.write))(javaMapFormat)
    }

    override implicit def optionFormat[T](
                                           implicit f: DynamoFormatV1[T]
                                         ): DynamoFormatV1[Option[T]] = {
      new DynamoFormatV1[Option[T]] {
        val attOps = implicitly[AmazonAttribute[AWSV1]]
        def read(av: AWSV1): Either[DynamoReadError, Option[T]] = {
          Option(av)
            .filterNot(attOps.isNull)
            .map(f.read(_).map(Some(_)))
            .getOrElse(Right(Option.empty[T]))
        }

        def write(t: Option[T]): AWSV1 = t.map(f.write).getOrElse(attOps.setNull(attOps.init))
        override val default = Some(None)
      }
    }

    override implicit def someFormat[T](implicit f: DynamoFormatV1[T]): DynamoFormatV1[Some[T]] =
      new DynamoFormatV1[Some[T]] {
        def read(av: AWSV1): Either[DynamoReadError, Some[T]] =
          Option(av).map(f.read(_).map(Some(_))).getOrElse(Left[DynamoReadError, Some[T]](MissingProperty))
        def write(t: Some[T]): AWSV1 = f.write(t.get)
      }
  }




  val a = DynamoFormatV1[String]


}
