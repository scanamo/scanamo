package org.scanamo.update

import cats.implicits.*
import org.scanamo.request.{ AttributeNamesAndValues, HasAttributes, RequestCondition }

case class UpdateAndCondition(update: UpdateExpression, condition: Option[RequestCondition] = None)
    extends HasAttributes {
  val attributes: AttributeNamesAndValues = update.attributes |+| condition.map(_.attributes).orEmpty
}
