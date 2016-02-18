package com.gu.scanamo

import cats.data._
import cats.std.list._

sealed abstract class DynamoReadError
case class PropertyReadError(name: String, problem: NonEmptyList[DynamoReadError]) extends DynamoReadError
case class NoPropertyOfType(propertyType: String) extends DynamoReadError
case class TypeCoercionError(e: Exception) extends DynamoReadError
case object MissingProperty extends DynamoReadError

object DynamoReadError {
  def describeAll(l: NonEmptyList[DynamoReadError]): String = l.unwrap.map(describe(_)).mkString(", ")
  def describe(d: DynamoReadError): String =  d match {
    case PropertyReadError(name, problem) => s"'${name}': ${describeAll(problem)}"
    case NoPropertyOfType(propertyType) => s"not of type: '$propertyType'"
    case TypeCoercionError(e) => s"could not be converted to desired type: $e"
    case MissingProperty => "missing"
  }
}