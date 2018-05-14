package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.error._
import cats.syntax.either._
import magnolia._
import scala.language.experimental.macros

abstract class EnumerationDynamoFormat[T] extends DynamoFormat[T]

/**
  * {{{
  * prop> sealed trait Animal
  * prop> case object Aardvark extends Animal
  * prop> case object Hippopotamus extends Animal
  * prop> case object Zebra extends Animal
  *
  * prop> import org.scalacheck._
  * prop> implicit val arbitraryAnimal: Arbitrary[Animal] = Arbitrary(Gen.oneOf(List(Aardvark, Hippopotamus, Zebra)))
  *
  * prop> (a: Animal) =>
  *     | DynamoFormat[Animal].read(DynamoFormat[Animal].write(a)) == Right(a)
  * }}}
  *
  * {{{
  * >>> DynamoFormat[Animal].write(Zebra).getS
  * Zebra
  * }}}
  */
trait DerivedEnumerationDynamoFormat {
  def dispatch[T](st: SealedTrait[EnumerationDynamoFormat, T]): EnumerationDynamoFormat[T] =
    new EnumerationDynamoFormat[T] {
      def read(av: AttributeValue): Either[DynamoReadError, T] = 
        Either.fromOption(
          for {
            typ <- Option(av.getS)
            sub <- st.subtypes.find(_.typeName.short == typ)
          } yield sub,
          NoPropertyOfType("S", av)
        ).flatMap(_.typeclass.read(av))

      def write(t: T): AttributeValue =
        st.dispatch(t) { sub => sub.typeclass.write(sub.cast(t)) }
    }
  
  def deriveEnum[T]: EnumerationDynamoFormat[T] = macro Magnolia.gen[T]
}
