package org.scanamo

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._

class EnumDynamoFormatTest extends AnyFunSuite with Matchers {
  test("automatic derivation for case object should only work if treating it as an enum") {
    val expected = DynamoValue.fromFields(
      "First" -> DynamoValue.fromString("First")
    )
    write(First) shouldBe (DynamoValue.fromString("First"))
    write[ExampleEnum](First) shouldBe expected
  }

  test("automatic derivation should handle fields with default values") {
    val obj = DynamoValue.fromFields("name" -> DynamoValue.fromString("Kirk"))
    val expected = Person(name = "Kirk")
    read[Person](obj) shouldBe (Right(expected))
  }

  def write[T](t: T)(implicit f: DynamoFormat[T]) = f.write(t)
  def read[T](v: DynamoValue)(implicit f: DynamoFormat[T]) = f.read(v)
}

sealed trait ExampleEnum
case object First extends ExampleEnum

case class Person(id: String = "1234", name: String)
