package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.scalatest.{FunSuite, Matchers}

class EnumDynamoFormatTest extends FunSuite with Matchers {

  test("automatic derivation for case object should only work if treating it as an enum") {
    write[ExampleEnum](First) shouldBe (DynamoValue.string("First"))
    "write(First)" shouldNot typeCheck
  }

  def write[T](t: T)(implicit f: DynamoFormat[T]) = f.write(t)
}

sealed trait ExampleEnum
case object First extends ExampleEnum
