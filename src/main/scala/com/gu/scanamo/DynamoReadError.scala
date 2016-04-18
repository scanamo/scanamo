package com.gu.scanamo

import cats.Semigroup
import cats.data.NonEmptyList
import cats.std.list._

sealed abstract class DynamoReadError
final case class NoPropertyOfType(propertyType: String) extends DynamoReadError
final case class TypeCoercionError(t: Throwable) extends DynamoReadError
final case object MissingProperty extends DynamoReadError

final case class PropertyReadError(name: String, problem: DynamoReadError)
final case class InvalidPropertiesError(errors: NonEmptyList[PropertyReadError]) extends DynamoReadError

object DynamoReadError {
  import cats.syntax.functor._

  def describe(d: DynamoReadError): String =  d match {
    case InvalidPropertiesError(problems) => problems.map(p => s"'${p.name}': ${describe(p.problem)}").unwrap.mkString(", ")
    case NoPropertyOfType(propertyType) => s"not of type: '$propertyType'"
    case TypeCoercionError(e) => s"could not be converted to desired type: $e"
    case MissingProperty => "missing"
  }

  import cats.syntax.semigroup._

  implicit object SemigroupInstance extends Semigroup[DynamoReadError] {
    override def combine(x: DynamoReadError, y: DynamoReadError): DynamoReadError = x match  {
      case InvalidPropertiesError(xErrors) => y match {
        case InvalidPropertiesError(yErrors) => InvalidPropertiesError(xErrors |+| yErrors)
        case _  => x
      }
      case _ => x
    }

  }
}