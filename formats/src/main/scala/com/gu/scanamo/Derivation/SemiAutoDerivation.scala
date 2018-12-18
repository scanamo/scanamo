package com.gu.scanamo.Derivation

import com.gu.scanamo.{DerivedDynamoFormat, DynamoFormat}
import com.gu.scanamo.export.Exported

trait SemiAutoDerivation extends DerivedDynamoFormat{

  final def deriveDynamoFormat[A](
    implicit exported: Exported[DynamoFormat[A]]
  ): DynamoFormat[A] = exported.instance

}
