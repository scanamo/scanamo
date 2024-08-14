package org.scanamo.update

import org.scalacheck.Arbitrary.*
import org.scalacheck.{ Arbitrary, Gen }
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.DynamoFormat
import org.scanamo.syntax.*

class UpdateExpressionTest extends AnyFunSpec with Matchers with org.scalatestplus.scalacheck.Checkers {
  implicit lazy val arbString: Arbitrary[String] = Arbitrary(Gen.alphaNumStr)

  def leaf: Gen[UpdateExpression] =
    for {
      s <- arbitrary[String]
      i <- arbitrary[Int]
      si <- arbitrary[Set[Int]]
      l <- arbitrary[List[String]]
      u <- Gen.oneOf(
        List(
          set(s, i),
          setIfNotExists(s, i),
          add(s, i),
          remove(s),
          delete(s, si),
          append(s, i),
          prepend(s, i),
          appendAll(s, l),
          prependAll(s, l)
        )
      )
    } yield u

  def genNode(level: Int): Gen[UpdateExpression] =
    for {
      left <- genTree(level)
      right <- genTree(level)
    } yield left and right

  def genTree(level: Int): Gen[UpdateExpression] =
    if (level >= 5) leaf
    else
      Gen.oneOf(leaf, genNode(level + 1))
  implicit lazy val update: Arbitrary[UpdateExpression] = Arbitrary(genTree(0))

  val stringList = DynamoFormat[List[String]]

  it("should have all value placeholders in the expression") {
    check((ue: UpdateExpression) => ue.attributes.values.keys.forall(s => ue.expression.contains(s)))
  }

  it("should have all name placeholders in the expression") {
    check((ue: UpdateExpression) => ue.attributes.names.keys.forall(s => ue.expression.contains(s)))
  }

  it("append/prepend should wrap scalar values in a list") {
    check { (s: String, v: String) =>
      append(s, v).unprefixedAttributeValues.get("update").exists(stringList.read(_) == Right(List(v)))
      prepend(s, v).unprefixedAttributeValues.get("update").exists(stringList.read(_) == Right(List(v)))
    }
  }

  it("appendAll/prependAll should take the value as a list") {
    check { (s: String, l: List[String]) =>
      appendAll(s, l).unprefixedAttributeValues.get("update").exists(stringList.read(_) == Right(l))
      prependAll(s, l).unprefixedAttributeValues.get("update").exists(stringList.read(_) == Right(l))
    }
  }
}
