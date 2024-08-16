package org.scanamo.update

import cats.implicits.*
import org.scanamo.request.{ AttributeNamesAndValues, RequestCondition }

case class UpdateAndCondition(update: UpdateExpression, condition: Option[RequestCondition] = None) {
  val attributes: AttributeNamesAndValues = update.attributes |+| condition.map(_.attributes).orEmpty
}
