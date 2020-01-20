package org.scanamo

private[scanamo] trait LowPriorityFormats {
  implicit final def exportedFormat[A](implicit E: generic.ExportedDynamoFormat[A]): DynamoFormat[A] = E.instance
}
