package org.scanamo.generic

import org.scanamo.DynamoFormat
import org.scanamo.generic.semiauto.deriveDynamoFormat

case class FoobarSemiAutoDerivation(value: Option[String])
object FoobarSemiAutoDerivation {
  implicit val dynamoFormatFoo: DynamoFormat[FoobarSemiAutoDerivation] = deriveDynamoFormat[FoobarSemiAutoDerivation]
}

