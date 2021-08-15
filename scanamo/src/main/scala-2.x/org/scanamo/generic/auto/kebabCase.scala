package org.scanamo.generic.auto

import org.scanamo.DynamoFormat
import org.scanamo.generic.{ AutoDerivation, Exported, KebabCaseFieldNamingMode }

object kebabCase extends AutoDerivation with KebabCaseFieldNamingMode {
  implicit final def genericDerivedFormat[A]: Exported[DynamoFormat[A]] = macro materializeImpl[A]
}
