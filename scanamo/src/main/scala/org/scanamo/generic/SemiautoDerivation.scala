package org.scanamo.generic

import magnolia.Magnolia
import org.scanamo.DynamoFormat
import scala.language.experimental.macros

final private case class Hidden[A](instance: A) extends AnyVal

trait SemiautoDerivation extends Derivation {
  type Typeclass[A] = HiddenDynamoFormat[A]

  final protected def build[A](df: DynamoFormat[A]): Typeclass[A] = Hidden(df)

  final protected def unbuild[A](tc: Typeclass[A]): DynamoFormat[A] = tc.instance

  final implicit def hiddenDynamoFormat[A]: HiddenDynamoFormat[A] = macro Magnolia.gen[A]

  final def deriveDynamoFormat[A](implicit S: HiddenDynamoFormat[A]): DynamoFormat[A] = S.instance
}
