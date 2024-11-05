package org.scanamo.request

import org.scanamo.{DynamoObject, DynamoValue}

trait Keyed

trait KeyedByKey extends Keyed {
  val key: DynamoObject
}

trait KeyedByItem extends Keyed {
  val item: DynamoValue
}