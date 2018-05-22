package com.gu.scanamo.generic

import com.gu.scanamo.DerivedDynamoFormat
import magnolia._
import scala.language.experimental.macros

final object auto extends DerivedDynamoFormat {
  implicit def deriveDynamoFormat[A]: Typeclass[A] = macro Magnolia.gen[A]
}