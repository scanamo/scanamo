package org.scanamo.generic.auto

import org.scanamo.DynamoFormat
import org.scanamo.generic.{AutoDerivation, Exported, SnakeCaseFieldNamingMode}

object snakeCase extends AutoDerivation with SnakeCaseFieldNamingMode {
  implicit final def genericDerivedFormat[A]: Exported[DynamoFormat[A]] = macro materializeImpl[A]
}
