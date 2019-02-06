package org.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.scalatest.{FunSuite, Matchers}
import org.scanamo.v1.DynamoFormatV1
class EnumDynamoFormatTest extends FunSuite with Matchers {

  test("automatic derivation for case object should only work if treating it as an enum") {
    write[ExampleEnum](First) shouldBe (new AttributeValue().withS("First"))
    "write(First)" shouldNot typeCheck
  }

  def write[T](t: T)(implicit f: DynamoFormatV1[T]) = f.write(t)
}

sealed trait ExampleEnum
case object First extends ExampleEnum
