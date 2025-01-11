package org.scanamo.request

import cats.Monoid
import org.scanamo.DynamoObject

case class AttributeNamesAndValues(names: Map[String, String], values: DynamoObject) {
  val isEmpty: Boolean = names.isEmpty && values.isEmpty
}

object AttributeNamesAndValues {
  val Empty: AttributeNamesAndValues = AttributeNamesAndValues(Map.empty, DynamoObject.empty)

  /** Technically, this isn't a Monoid - it's not strictly associative (combining `Map`s can arbitrarily annihilate
    * different keys), making it just a 'unital magma'... practically though, the difference shouldn't be a problem.
    */
  implicit val m: Monoid[AttributeNamesAndValues] =
    Monoid.instance(Empty, (x, y) => AttributeNamesAndValues(x.names ++ y.names, x.values <> y.values))
}
