package com.gu.scanamo

import cats.data.ValidatedNel
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.syntax.validated._
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.error._
import magnolia._
import scala.language.experimental.macros

import collection.JavaConverters._

object DerivedDynamoFormat {
  type Typeclass[A] = DynamoFormat[A]
  type Valid[A] = ValidatedNel[PropertyReadError, A]

  val nullAv = new AttributeValue().withNULL(true)

  def combine[T](cc: CaseClass[Typeclass, T]): Typeclass[T] = {
    def decodeField[A](m: Map[String, AttributeValue])(p: Param[DynamoFormat, A]): Valid[p.PType] =
      p.typeclass.read(m.get(p.label).getOrElse(nullAv)).leftMap(PropertyReadError(p.label, _)).toValidatedNel

    def decode(av: AttributeValue): Valid[Seq[Any]] =
      Option(av.getM).map(_.asScala.toMap) match {
        case Some(m) => cc.parameters.toList.traverse(decodeField(m)(_))
        case None => PropertyReadError(cc.typeName.full, NoPropertyOfType("M", av)).invalidNel
      }

    new DynamoFormat[T] {
      def read(av: AttributeValue): Either[DynamoReadError, T] = 
        decode(av).fold(fe => Left(InvalidPropertiesError(fe)), fa => Right(cc.rawConstruct(fa)))

      def write(t: T): AttributeValue =
        new AttributeValue().withM(cc.parameters.map { p => 
          p.label -> p.typeclass.write(p.dereference(t)) 
        }.toMap.asJava)
    }
  }

  def dispatch[T](st: SealedTrait[Typeclass, T]): Typeclass[T] =
    new DynamoFormat[T] {
      def read(av: AttributeValue): Either[DynamoReadError, T] = 
        st.subtypes.foldRight(Left(NoSubtypeOfType(st.typeName.full, av)): Either[DynamoReadError, T]) { case (sub, r) =>
          r.recoverWith { case _: DynamoReadError => sub.typeclass.read(av) }
        }

      def write(t: T): AttributeValue =
        st.dispatch(t) { sub => sub.typeclass.write(sub.cast(t)) }
    }
  
  def derive[T]: Typeclass[T] = macro Magnolia.gen[T]
}
