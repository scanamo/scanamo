package org.scanamo
package refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive

import org.scanamo.error.TypeCoercionError
import com.amazonaws.services.dynamodbv2.model.AttributeValue

import org.scalatest.{ FlatSpec, Matchers }

class RefinedDynamoFormatSpec extends FlatSpec with Matchers {

  type PosInt = Int Refined Positive

  "DynamoFormat[PosInt]" should "read a positive integer value" in {
    val valueToRead = new AttributeValue().withN("10")

    val valueRead = DynamoFormat[PosInt].read(valueToRead)
    valueRead shouldBe Right(10: PosInt)
  }

  it should "fail to read non positive integers" in {
    val valueToRead = new AttributeValue().withN("-234")

    val expectedErrorMsg = "Predicate failed: (-234 > 0)."

    val valueRead = DynamoFormat[PosInt].read(valueToRead)
    valueRead should matchPattern {
      case Left(e: TypeCoercionError) if e.t.getMessage == expectedErrorMsg =>
    }
  }

  it should "write positive integers" in {
    val expectedValue = new AttributeValue().withN("123")

    val valueWritten = DynamoFormat[PosInt].write(123)

    valueWritten shouldBe expectedValue
  }

}
