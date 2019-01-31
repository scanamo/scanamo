package org.scanamo

import org.scalatest.{FunSuite, Matchers}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class EnumDynamoFormatTest extends FunSuite with Matchers {

  test("automatic derivation for case object should only work if treating it as an enum") {
    write[ExampleEnum](First) shouldBe AttributeValue.builder().s("First").build()
    "write(First)" shouldNot typeCheck
  }

  def write[T](t: T)(implicit f: DynamoFormat[T]) = f.write(t)
}

sealed trait ExampleEnum
case object First extends ExampleEnum
