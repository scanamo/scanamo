package org.scanamo.request

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.DynamoObject

import scala.annotation.nowarn

class RequestConditionTest extends AnyFunSpec with Matchers with OptionValues {
  it("can try to support legacy code using the deprecated optional requestCondition.dynamoValues") {
    val condition = RequestCondition("", AttributeNamesAndValues(Map.empty, DynamoObject.empty))

    @nowarn("cat=deprecation")
    val dynamoValues: Option[DynamoObject] = condition.dynamoValues // for legacy clients
    dynamoValues.value shouldBe condition.attributes.values
  }
}
