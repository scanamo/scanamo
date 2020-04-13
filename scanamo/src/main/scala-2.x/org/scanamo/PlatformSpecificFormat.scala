package org.scanamo

import magnolia.Magnolia
import org.scanamo.generic.{ AutoDerivationUnlocker, Derivation }

import scala.language.experimental.macros

trait PlatformSpecificFormat extends Derivation {
  implicit final def genericFormat[A](implicit U: AutoDerivationUnlocker): DynamoFormat[A] =
    macro deriveGenericFormat[A]

  def deriveGenericFormat[A: c.WeakTypeTag](c: scala.reflect.macros.whitebox.Context)(U: c.Tree): c.Tree = {
    val _ = U
    Magnolia.gen[A](c)
  }
}