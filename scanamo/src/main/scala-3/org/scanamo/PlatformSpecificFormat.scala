package org.scanamo

trait PlatformSpecificFormat {
    inline given derived[T](using m: Mirror.Of[T]) as DynamoFormat[T] = ???
}