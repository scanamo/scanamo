package org.scanamo.generic.auto

import org.scanamo.DynamoFormat
import org.scanamo.generic.{ AutoDerivation, Exported, PascalCaseFieldNamingMode }

object pascalCase extends AutoDerivation with PascalCaseFieldNamingMode {
  implicit final def genericDerivedFormat[A]: Exported[DynamoFormat[A]] = macro materializeImpl[A]
}
