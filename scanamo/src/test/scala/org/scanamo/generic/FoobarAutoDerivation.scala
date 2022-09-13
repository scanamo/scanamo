package org.scanamo.generic

import org.scanamo.{ DynamoFormat, TypeCoercionError }

object FoobarAutoDerivation {
  def fromString(s: String): Either[TypeCoercionError, FoobarAutoDerivation] =
    s match {
      case "foo" => Right(FoobarAutoDerivation(()))
      case _     => Left(TypeCoercionError(new RuntimeException(s"$s is not a foo")))
    }

  implicit val dynamoFormatFoo: DynamoFormat[FoobarAutoDerivation] =
    DynamoFormat.xmap[FoobarAutoDerivation, String](fromString, (_: FoobarAutoDerivation) => "foo")
}

case class FoobarAutoDerivation(value: Unit)
