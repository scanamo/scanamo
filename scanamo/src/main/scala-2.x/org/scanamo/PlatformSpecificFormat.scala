package org.scanamo

import org.scanamo.generic.{ AutoDerivationUnlocker, Derivation }

import scala.language.experimental.macros

trait PlatformSpecificFormat {
  implicit final def genericFormat[A](implicit U: AutoDerivationUnlocker): DynamoFormat[A] =
    macro Derivation.deriveGenericFormat[A]
}
