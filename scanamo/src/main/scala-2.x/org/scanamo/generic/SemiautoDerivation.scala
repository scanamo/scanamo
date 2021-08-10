package org.scanamo.generic

import org.scanamo.DynamoFormat

trait SemiautoDerivation extends Derivation with magnolia1.Derivation[DynamoFormat] {

  // Members declared in magnolia1.CommonDerivation
  override def join[T](ctx: magnolia1.CaseClass[Typeclass, T]): Typeclass[T] = ???

  override def split[T](ctx: magnolia1.SealedTrait[Typeclass, T]): Typeclass[T] = ???


  final def deriveDynamoFormat[A]: DynamoFormat[A] = ??? // derived[A]
}
