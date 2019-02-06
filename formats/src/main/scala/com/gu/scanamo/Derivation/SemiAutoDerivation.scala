package org.scanamo.Derivation

import org.scanamo.DerivedDynamoFormat
import org.scanamo.Foo.DynamoFormatV1
import org.scanamo.export.Exported

trait SemiAutoDerivation extends DerivedDynamoFormat {

  final def deriveDynamoFormat[A](
    implicit exported: Exported[DynamoFormatV1[A]]
  ): DynamoFormatV1[A] = exported.instance

}
