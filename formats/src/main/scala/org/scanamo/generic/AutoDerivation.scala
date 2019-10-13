package org.scanamo.generic

import magnolia.Magnolia
import org.scanamo.ExportedDynamoFormat
import scala.language.experimental.macros

trait AutoDerivation extends GenericDerivation {

  implicit def exportDynamoFormat[T]: ExportedDynamoFormat[T] = macro Magnolia.gen[T]

}
