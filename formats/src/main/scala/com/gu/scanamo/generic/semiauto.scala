package com.gu.scanamo.generic

import com.gu.scanamo.DerivedDynamoFormat
import magnolia._
import scala.language.experimental.macros

final object semiauto extends DerivedDynamoFormat {
  final def deriveDynamoFormat[A]: Typeclass[A] = macro Magnolia.gen[A]
}