package org.scanamo

import org.scanamo.aws.models.AmazonAttribute
import org.scanamo.error._

import scala.reflect.ClassTag
import java.util.UUID

import cats.Applicative
import org.scanamo.aws.models.AmazonAttribute
import org.scanamo.error._
import org.scanamo.export.Exported

//import scala.collection.immutable.SortedMap
import scala.reflect.ClassTag

abstract class DynamoFormat[T, Value: AmazonAttribute] {
  def read(av: Value): Either[DynamoReadError, T]
  def write(t: T): Value
  def default: Option[T] = None
}


object v1 {

  object DynamoFormat extends DynamoFormatAPI[com.amazonaws.services.dynamodbv2.model.AttributeValue] with EnumDynamoFormat {
    def find[T, Attribute: AmazonAttribute](implicit instance: Exported[DynamoFormat[T, Attribute]]) : DynamoFormat[T, Attribute] = {
      dynamoFormat[T, Attribute]
    }
  }
}


abstract class DynamoFormatAPI[AttributeValue: AmazonAttribute]  {
  private def attribute[T](
    decode: AmazonAttribute[AttributeValue] => AttributeValue => T,
    propertyType: String
  )(
    encode: AmazonAttribute[AttributeValue] => AttributeValue => T => AttributeValue
  ): DynamoFormat[T, AttributeValue] =
    new DynamoFormat[T, AttributeValue] {
      import cats.syntax.either._


      override def read(av: AttributeValue): Either[DynamoReadError, T] = {
        val opt = Option(decode(implicitly[AmazonAttribute[AttributeValue]])(av))
        Either.fromOption(opt, NoPropertyOfType(propertyType, av))
      }
      override def write(t: T): AttributeValue = {
        val zero = implicitly[AmazonAttribute[AttributeValue]]
        encode(zero)(zero.init)(t)
      }
    }

  def iso[A, B](r: B => A)(w: A => B)(implicit f: DynamoFormat[B, AttributeValue]) =
    new DynamoFormat[A, AttributeValue] {
      override def read(item: AttributeValue): Either[DynamoReadError, A] = f.read(item).map(r)
      override def write(t: A): AttributeValue = f.write(w(t))
      override val default: Option[A] = f.default.map(r)
    }

  def xmap[A, B](
    r: B => Either[DynamoReadError, A]
  )(w: A => B)(implicit f: DynamoFormat[B, AttributeValue]) = new DynamoFormat[A, AttributeValue] {
    override def read(item: AttributeValue): Either[DynamoReadError, A] = f.read(item).flatMap(r)
    override def write(t: A): AttributeValue = f.write(w(t))
  }

  import cats.NotNull
//  import cats.instances.list._
//  import cats.instances.vector._
//  import cats.syntax.either._
//  import cats.syntax.traverse._

  def coercedXmap[A, B, T >: scala.Null <: scala.Throwable](
    read: B => A
  )(write: A => B)(implicit f: DynamoFormat[B, AttributeValue], T: ClassTag[T], NT: NotNull[T]) =
    xmap(coerce[B, A, T](read))(write)

  implicit def stringFormat =
    attribute[String](_.getString, "S")(_.setString)

  private def numFormat: DynamoFormat[String, AttributeValue] =
    attribute[String](_.getNumericString, "N")(_.setNumericString)

  private def coerceNumber[N](f: String => N): String => Either[DynamoReadError, N] =
    coerce[String, N, NumberFormatException](f)

  import cats.NotNull
//  import cats.instances.list._
//  import cats.instances.vector._
  import cats.syntax.either._
//  import cats.syntax.traverse._

  private def coerce[A, B, T >: scala.Null <: scala.Throwable](
    f: A => B
  )(implicit T: ClassTag[T], NT: NotNull[T]): A => Either[DynamoReadError, B] =
    a => Either.catchOnly[T](f(a)).leftMap(TypeCoercionError(_))

  implicit def longFormat: DynamoFormat[Long, AttributeValue] =
    xmap[Long, String](coerceNumber(_.toLong))(_.toString)(numFormat)

  implicit def intFormat =
    xmap[Int, String](coerceNumber(_.toInt))(_.toString)(numFormat)

  implicit val floatFormat = xmap(coerceNumber(_.toFloat))(_.toString)(numFormat)

  implicit val doubleFormat = xmap(coerceNumber(_.toDouble))(_.toString)(numFormat)

  implicit val bigDecimalFormat =
    xmap(coerceNumber(BigDecimal(_)))(_.toString)(numFormat)

  implicit val shortFormat = xmap(coerceNumber(_.toShort))(_.toString)(numFormat)

  implicit def byteFormat = xmap(coerceNumber(_.toByte))(_.toString)(numFormat)

  implicit def byteArrayFormat = attribute[Array[Byte]](_.getBytesArray, "B")(_.setBytesArray)

  implicit val uuidFormat = {
    coercedXmap[UUID, String, IllegalArgumentException](UUID.fromString)(_.toString)
  }

  val javaListFormat = attribute[List[AttributeValue]](_.getList, "L")(_.setList)

  implicit def ff : Applicative[Either[DynamoReadError, ?]] = ???

  implicit def listFormat[T](
    implicit r: DynamoFormat[T, AttributeValue]
  ): DynamoFormat[List[T], AttributeValue] = {
//    import cats.{Applicative, NotNull}
    import cats.instances.list._
//    import cats.instances.vector._
//    import cats.syntax.either._
    import cats.syntax.traverse._

    xmap[List[T], List[AttributeValue]](_.traverse[Either[DynamoReadError, ?], T](r.read))(items => items.map(r.write))(javaListFormat)

  }
  implicit def seqFormat[T](
                             implicit f: DynamoFormat[T, AttributeValue]
                           ): DynamoFormat[Seq[T], AttributeValue] =
    xmap[Seq[T], List[T]](l => Right(l))(_.toList)

  implicit def vectorFormat[T](
                                implicit f: DynamoFormat[T, AttributeValue]
                              ): DynamoFormat[Vector[T], AttributeValue] = {

//    import cats.{Applicative, NotNull}
//    import cats.instances.list._
    import cats.instances.vector._
//    import cats.syntax.either._
    import cats.syntax.traverse._
//    import cats.implicits._


    xmap[Vector[T], List[AttributeValue]](_.toVector.traverse(f.read))(
      _.toList.map(f.write)
    )(javaListFormat)
                              }
  implicit def arrayFormat[T: ClassTag](
                                         implicit f: DynamoFormat[T, AttributeValue]
                                       ): DynamoFormat[Array[T], AttributeValue] = {
    import cats.instances.list._
//    import cats.instances.vector._
//        import cats.syntax.either._
    import cats.syntax.traverse._

    xmap[Array[T], List[AttributeValue]](_.traverse(f.read).map(_.toArray))(
      _.toArray.map(f.write).toList
    )(javaListFormat)
                                       }

  private def numSetFormat[T](
                               r: String => Either[DynamoReadError, T]
                             )(w: T => String): DynamoFormat[Set[T], AttributeValue] =
    new DynamoFormat[Set[T], AttributeValue] {

      import cats.instances.list._
              import cats.syntax.either._
      import cats.syntax.traverse._

      val hellper = implicitly[AmazonAttribute[AttributeValue]]

      override def read(av: AttributeValue): Either[DynamoReadError, Set[T]] =
        for {
          ns <- Either.fromOption(
            if (hellper.isNull(av)) Some(Nil) else Option(hellper.getNS(av)),
            NoPropertyOfType("NS", av)
          )
          set <- ns.traverse(r)
        } yield set.toSet
      // Set types cannot be empty
      override def write(t: Set[T]): AttributeValue = {
        val zero = hellper.init
        t.toList match {
          case Nil => hellper.setNull(zero)
          case xs  => hellper.setNS(zero)(xs.map(w))
        }
      }
      override val default: Option[Set[T]] = Some(Set.empty)
    }

  implicit val intSetFormat: DynamoFormat[Set[Int], AttributeValue] = numSetFormat(coerceNumber(_.toInt))(_.toString)

  implicit val longSetFormat: DynamoFormat[Set[Long], AttributeValue] = numSetFormat(coerceNumber(_.toLong))(_.toString)

  implicit val floatSetFormat: DynamoFormat[Set[Float], AttributeValue] =
    numSetFormat(coerceNumber(_.toFloat))(_.toString)

  implicit val doubleSetFormat: DynamoFormat[Set[Double], AttributeValue] =
    numSetFormat(coerceNumber(_.toDouble))(_.toString)

  implicit val BigDecimalSetFormat: DynamoFormat[Set[BigDecimal], AttributeValue] =
    numSetFormat(coerceNumber(BigDecimal(_)))(_.toString)

  implicit val stringSetFormat: DynamoFormat[Set[String], AttributeValue] =
    new DynamoFormat[Set[String], AttributeValue] {

//      import cats.{Applicative, NotNull}
//      import cats.instances.list._
//      import cats.instances.vector._
      import cats.syntax.either._
//      import cats.syntax.traverse._

      private val helper = implicitly[AmazonAttribute[AttributeValue]]

      override def read(av: AttributeValue): Either[DynamoReadError, Set[String]] =
        for {
          ss <- Either.fromOption(
            if (helper.isNull(av)) Some(Nil) else Option(helper.getSS(av)),
            NoPropertyOfType("SS", av)
          )
        } yield ss.toSet
      // Set types cannot be empty
      override def write(t: Set[String]): AttributeValue = t.toList match {
        case Nil => helper.setNull(helper.init)
        case xs  => helper.setSS(helper.init)(xs)
      }
      override val default: Option[Set[String]] = Some(Set.empty)
    }

//  private val javaMapFormat = attribute[Map[String, AttributeValue]](_.getMap, "M")(_.setMap)

  implicit def mapFormat[V](implicit f: DynamoFormat[V, AttributeValue]): DynamoFormat[Map[String, V], AttributeValue] =
//    xmap[Map[String, V], Map[String, AttributeValue]](
//      m => (SortedMap[String, AttributeValue]() ++ m).traverse(f.read)
//    )(_.mapValues(f.write))(javaMapFormat)
  ???

  implicit def optionFormat[T](implicit f: DynamoFormat[T, AttributeValue]) =
    new DynamoFormat[Option[T], AttributeValue] {

      val attOps = implicitly[AmazonAttribute[AttributeValue]]

      def read(av: AttributeValue): Either[DynamoReadError, Option[T]] =
        Option(av)
          .filter(x => attOps.isNull(x))
          .map(f.read(_).map(Some(_)))
          .getOrElse(Right(Option.empty[T]))

      def write(t: Option[T]): AttributeValue = t.map(f.write).getOrElse(attOps.setNull(attOps.init))
      override val default = Some(None)
    }


  implicit def someFormat[T](implicit f: DynamoFormat[T, AttributeValue]) =
    new DynamoFormat[Some[T], AttributeValue] {
      def read(av: AttributeValue): Either[DynamoReadError, Some[T]] =
        Option(av).map(f.read(_).map(Some(_))).getOrElse(Left[DynamoReadError, Some[T]](MissingProperty))

      def write(t: Some[T]): AttributeValue = f.write(t.get)
    }
}


//  trait DynamoFormatV1[T] extends DynamoFormat[T, com.amazonaws.services.dynamodbv2.model.AttributeValue]

//  object DynamoFormatV1 extends DynamoFormatAPI[com.amazonaws.services.dynamodbv2.model.AttributeValue] with EnumDynamoFormat {
//    def apply[T](implicit d: DynamoFormat[T, com.amazonaws.services.dynamodbv2.model.AttributeValue]): DynamoFormatV1[T] = {
//      ???
//    }
//  }

