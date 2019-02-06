package org.scanamo.Derivation

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.scanamo.{DerivedDynamoFormat, DynamoFormat}
import org.scanamo.export.Exported

trait SemiAutoDerivation extends DerivedDynamoFormat[AttributeValue] {

  final def deriveDynamoFormat[A](
    implicit exported: Exported[DynamoFormat[A, AttributeValue]]
  ): DynamoFormat[A, AttributeValue] = exported.instance

}
