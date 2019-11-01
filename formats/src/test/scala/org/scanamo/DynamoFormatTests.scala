package org.scanamo

import cats.Eq
import cats.laws._
import cats.laws.discipline._
import org.scalacheck.{ Arbitrary, Prop }
import org.scanamo.error.DynamoReadError
import org.typelevel.discipline.Laws

trait DynamoFormatLaws[A] {
  def format: DynamoFormat[A]

  def roundTrip(a: A): IsEq[Either[DynamoReadError, A]] = format.read(format.write(a)) <-> Right(a)
}

object DynamoFormatLaws {
  def apply[A](implicit format0: DynamoFormat[A]): DynamoFormatLaws[A] =
    new DynamoFormatLaws[A] {
      val format = format0
    }
}

trait DynamoFormatTests[A] extends Laws {
  def laws: DynamoFormatLaws[A]

  def instance(implicit arb: Arbitrary[A], eq: Eq[A]): RuleSet =
    new SimpleRuleSet(
      name = "DynamoFormat",
      "roundTrip" -> Prop.forAll(laws.roundTrip(_))
    )
}

object DynamoFormatTests {
  def apply[A: DynamoFormat]: DynamoFormatTests[A] = new DynamoFormatTests[A] {
    val laws: DynamoFormatLaws[A] = DynamoFormatLaws[A]
  }
}