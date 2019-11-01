package org.scanamo

package object generic {
  private[scanamo] type ExportedDynamoFormat[A] = Exported[DynamoFormat[A]]

  private[generic] type HiddenDynamoFormat[A] = Hidden[DynamoFormat[A]]
}
