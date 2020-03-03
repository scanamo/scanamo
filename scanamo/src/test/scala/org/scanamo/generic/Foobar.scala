package org.scanamo.generic

import org.scanamo.{DynamoFormat, TypeCoercionError}

object Foobar {
  def fromString(s: String): Either[TypeCoercionError, Foobar] = s match {
    case "foo" => Right(Foobar(()))
    case _     => Left(TypeCoercionError(new RuntimeException(s"$s is not a foo")))
  }

  implicit val dynamoFormatFoo: DynamoFormat[Foobar] =
    DynamoFormat.xmap[Foobar, String](fromString, (_: Foobar) => "foo")
}

case class Foobar(value: Unit)
