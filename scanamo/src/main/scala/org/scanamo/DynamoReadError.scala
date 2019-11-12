package org.scanamo

import cats.data.NonEmptyList
import cats.Show
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException

sealed abstract class ScanamoError
final case class ConditionNotMet(e: ConditionalCheckFailedException) extends ScanamoError

sealed abstract class DynamoReadError extends ScanamoError
final case class NoPropertyOfType(propertyType: String, actual: DynamoValue) extends DynamoReadError
final case class TypeCoercionError(t: Throwable) extends DynamoReadError
final case object MissingProperty extends DynamoReadError
final case class InvalidPropertiesError(errors: NonEmptyList[(String, DynamoReadError)]) extends DynamoReadError

object DynamoReadError {
  implicit object ShowInstance extends Show[DynamoReadError] {
    def show(e: DynamoReadError): String = describe(e)
  }

  def describe(d: DynamoReadError): String = d match {
    case InvalidPropertiesError(problems) =>
      problems.toList.map(p => s"'${p._1}': ${describe(p._2)}").mkString(", ")
    case NoPropertyOfType(propertyType, actual) => s"not of type: '$propertyType' was '$actual'"
    case TypeCoercionError(e)                   => s"could not be converted to desired type: $e"
    case MissingProperty                        => "missing"
  }
}
