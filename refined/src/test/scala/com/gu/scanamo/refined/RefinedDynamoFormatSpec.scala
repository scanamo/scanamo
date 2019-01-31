package org.scanamo
package refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import org.scanamo.error.TypeCoercionError
import org.scalatest.{FlatSpec, Matchers}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class RefinedDynamoFormatSpec extends FlatSpec with Matchers {

  type PosInt = Int Refined Positive

  "DynamoFormat[PosInt]" should "read a positive integer value" in {
    val valueToRead = AttributeValue.builder().n("10").build()

    val valueRead = DynamoFormat[PosInt].read(valueToRead)
    valueRead shouldBe Right(10: PosInt)
  }

  it should "fail to read non positive integers" in {
    val valueToRead = AttributeValue.builder().n("-234").build()

    val expectedErrorMsg = "Predicate failed: (-234 > 0)."

    val valueRead = DynamoFormat[PosInt].read(valueToRead)
    valueRead should matchPattern {
      case Left(e: TypeCoercionError) if e.t.getMessage == expectedErrorMsg =>
    }
  }

  it should "write positive integers" in {
    val expectedValue = AttributeValue.builder().n("123").build()

    val valueWritten = DynamoFormat[PosInt].write(123)

    valueWritten shouldBe expectedValue
  }

}
