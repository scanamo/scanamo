package com.gu.scanamo.error

import cats.data.NonEmptyList
import cats.Show
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ConditionalCheckFailedException}

sealed abstract class ScanamoError
final case class ConditionNotMet(e: ConditionalCheckFailedException) extends ScanamoError

sealed abstract class DynamoReadError extends ScanamoError
final case class NoPropertyOfType(propertyType: String, actual: AttributeValue) extends DynamoReadError
final case class NoSubtypeOfType(typeName: String) extends DynamoReadError
final case class TypeCoercionError(t: Throwable) extends DynamoReadError
final case class InvalidPropertiesError(errors: NonEmptyList[DynamoReadError]) extends DynamoReadError
final case class PropertyReadError(name: String, error: DynamoReadError) extends DynamoReadError
final case object MissingProperty extends DynamoReadError

object DynamoReadError {
  implicit object ShowInstance extends Show[DynamoReadError] {
    def show(e: DynamoReadError): String = describe(e)
  }

  def describe(d: DynamoReadError): String = d match {
    case InvalidPropertiesError(problems)       => problems.toList.map(describe).mkString(", ")
    case NoPropertyOfType(propertyType, actual) => s"not of type: '$propertyType' was '$actual'"
    case NoSubtypeOfType(typeName)              => s"empty sealed trait '$typeName' cannot be instantiated"
    case TypeCoercionError(e)                   => s"could not be converted to desired type: $e"
    case PropertyReadError(n, e)                => s"'$n': ${describe(e)}"
    case MissingProperty                        => "missing"
  }
}
