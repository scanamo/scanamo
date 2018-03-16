package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.error.TypeCoercionError
import org.scalatest.{EitherValues, FunSuite, Matchers}

class DynamoFormatTest extends FunSuite with Matchers with EitherValues {

  test("Handles trying to deserialize something that's not a map to a product") {
    case class Product(foo: String, bar: Int)
    DynamoFormat[Product].read(new AttributeValue().withS("bar")).left.value shouldBe a[TypeCoercionError]
  }

  test("Handles trying to deserialize something that's not a map to a coproduct") {
    sealed trait CoProduct
    case class Foo(f1: String) extends CoProduct
    case class Bar(f2: Int) extends CoProduct
    DynamoFormat[CoProduct].read(new AttributeValue().withS("bar")).left.value shouldBe a [TypeCoercionError]
  }

}
