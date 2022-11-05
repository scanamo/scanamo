package org.scanamo

trait PlatformSpecificFormat {
  implicit def generic[A](implicit A: org.scanamo.generic.Exported[DynamoFormat[A]]): DynamoFormat[A] =
    A.instance
}
