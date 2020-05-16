package org.scanamo

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

package object query {

  implicit val arbAttributeName: Arbitrary[AttributeName] = Arbitrary(genAttributeName)
  
  def genAttributeName: Gen[AttributeName] = Gen.oneOf(topLevel, Gen.delay(listElement), Gen.delay(mapElement))

  def topLevel: Gen[AttributeName] = Gen.alphaNumStr.map(AttributeName.of(_))

  def listElement: Gen[AttributeName] = for { a <- genAttributeName; i <- arbitrary[Int] } yield a ! i

  def mapElement: Gen[AttributeName] = for { a <- genAttributeName; k <- Gen.alphaNumStr } yield a \ k

}

