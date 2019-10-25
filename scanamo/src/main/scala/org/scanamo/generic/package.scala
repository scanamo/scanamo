package org.scanamo

package object generic {
  private[generic] type HiddenDynamoFormat[A] = Hidden[DynamoFormat[A]]
}
