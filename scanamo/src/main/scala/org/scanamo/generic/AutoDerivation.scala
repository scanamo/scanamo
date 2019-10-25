package org.scanamo.generic

import org.scanamo.{ Exported, ExportedDynamoFormat }
import magnolia.Magnolia
import org.scanamo.DynamoFormat

import scala.language.experimental.macros

trait AutoDerivation extends Derivation {

  type Typeclass[A] = ExportedDynamoFormat[A]

  protected def build[A](df: DynamoFormat[A]): ExportedDynamoFormat[A] = Exported(df)

  protected def unbuild[A](tc: Typeclass[A]): DynamoFormat[A] = tc.instance

  implicit def exportDynamoFormat[A]: ExportedDynamoFormat[A] = macro Magnolia.gen[A]

}
