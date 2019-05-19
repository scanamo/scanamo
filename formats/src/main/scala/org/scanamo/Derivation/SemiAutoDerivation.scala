package org.scanamo.Derivation

import org.scanamo.{DerivedDynamoFormat, DynamoFormat}
import org.scanamo.export.Exported

trait SemiAutoDerivation extends DerivedDynamoFormat {

  final def deriveDynamoFormat[A](
    implicit exported: Exported[DynamoFormat[A]]
  ): DynamoFormat[A] = exported.instance

}
