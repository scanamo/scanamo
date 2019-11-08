package org.scanamo.generic

import magnolia.Magnolia
import org.scanamo.DynamoFormat

import scala.language.experimental.macros

trait AutoDerivation extends Derivation {
  type Typeclass[A] = ExportedDynamoFormat[A]

  final protected def build[A](df: DynamoFormat[A]): ExportedDynamoFormat[A] = Exported(df)

  final protected def unbuild[A](tc: Typeclass[A]): DynamoFormat[A] = tc.instance

  final implicit def exportDynamoFormat[A]: ExportedDynamoFormat[A] = macro Magnolia.gen[A]
}
