package org.scanamo

import org.scalatest.{ FunSuite, Matchers }
import org.scanamo.generic.auto._

class EnumDynamoFormatTest extends FunSuite with Matchers {
  test("automatic derivation for case object should only work if treating it as an enum") {
    val expected = DynamoValue.fromFields(
      "tag" -> DynamoValue.fromString("First"),
      "value" -> DynamoValue.fromString("First")
    )
    write(First) shouldBe (DynamoValue.fromString("First"))
    write[ExampleEnum](First) shouldBe (expected)
  }

  def write[T](t: T)(implicit f: DynamoFormat[T]) = f.write(t)
}

sealed trait ExampleEnum
case object First extends ExampleEnum
