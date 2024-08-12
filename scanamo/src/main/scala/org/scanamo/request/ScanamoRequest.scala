/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo.request

import cats.implicits.*
import org.scanamo.query.{ Condition, Query }
import org.scanamo.update.{ UpdateAndCondition, UpdateExpression }
import org.scanamo.{ DeleteReturn, DynamoObject, DynamoValue, PutReturn }

trait HasAttributes {
  def attributes: AttributeNamesAndValues
}

trait AttributesSummation {
  val tableName: String
  def attributesSources: Seq[HasAttributes]
  lazy val attributes: AttributeNamesAndValues =
    attributesSources.foldLeft(AttributeNamesAndValues.Empty)(_ |+| _.attributes)
}

case class ScanamoPutRequest(
  tableName: String,
  item: DynamoValue,
  condition: Option[RequestCondition],
  ret: PutReturn
) extends AttributesSummation {
  val attributesSources: Seq[HasAttributes] = condition.toSeq
}

case class ScanamoDeleteRequest(
  tableName: String,
  key: DynamoObject,
  condition: Option[RequestCondition],
  ret: DeleteReturn
) extends AttributesSummation {
  val attributesSources: Seq[HasAttributes] = condition.toSeq
}

trait HasUpdateExpressionWithCondition {
  def attributeNames: Map[String, String]

  val condition: Option[RequestCondition]

  def combinedAttributeNames: Map[String, String] =
    attributeNames ++ condition.map(_.attributeNames).getOrElse(Map.empty)

  def dynamoValues: DynamoObject
  def combinedAttributeValues: DynamoObject =
    condition.flatMap(_.dynamoValues).fold(dynamoValues)(_ <> dynamoValues)
}

case class ScanamoUpdateRequest(
  tableName: String,
  key: DynamoObject,
  updateAndCondition: UpdateAndCondition
) extends AttributesSummation {
  val attributesSources: Seq[HasAttributes] = Seq(updateAndCondition)

  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def updateExpression: String = updateAndCondition.update.expression
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def attributeNames: Map[String, String] = updateAndCondition.update.attributes.names
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def dynamoValues: DynamoObject = updateAndCondition.update.attributes.values
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def addEmptyList: Boolean = updateAndCondition.update.addEmptyList
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def condition: Option[RequestCondition] = updateAndCondition.condition
}

case class ScanamoScanRequest(
  tableName: String,
  index: Option[String],
  options: ScanamoQueryOptions
) extends AttributesSummation {
  lazy val attributesSources: Seq[HasAttributes] = options.filterCondition.toSeq
}

case class ScanamoQueryRequest(
  tableName: String,
  index: Option[String],
  query: Query[_],
  options: ScanamoQueryOptions
) extends AttributesSummation {
  lazy val queryCondition: RequestCondition = query.apply

  lazy val attributesSources: Seq[HasAttributes] = Seq(queryCondition) ++ options.filterCondition
}

case class ScanamoQueryOptions(
  consistent: Boolean,
  ascending: Boolean,
  limit: Option[Int],
  exclusiveStartKey: Option[DynamoObject],
  filter: Option[Condition[_]]
) {
  lazy val filterCondition: Option[RequestCondition] = filter.map(_.apply.runEmptyA.value)
}
object ScanamoQueryOptions {
  val default = ScanamoQueryOptions(false, true, None, None, None)
}

case class RequestCondition(
  expression: String,
  attributes: AttributeNamesAndValues
) extends HasAttributes {
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def this(
    expression: String,
    attributeNames: Map[String, String],
    dynamoValues: Option[DynamoObject]
  ) = this(expression, AttributeNamesAndValues(attributeNames, dynamoValues.orEmpty))
  @deprecated("Use `attributes.names` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def attributeNames: Map[String, String] = attributes.names
  @deprecated("Use `attributes.values` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def dynamoValues: Option[DynamoObject] = Some(attributes.values)
}

trait TransactPunk {
  val tableName: String
}

case class TransactPutItem(
  tableName: String,
  item: DynamoValue,
  condition: Option[RequestCondition]
) extends TransactPunk with AttributesSummation {
  override val attributesSources: Seq[HasAttributes] = condition.toSeq
}

case class TransactUpdateItem(
  tableName: String,
  key: DynamoObject,
  updateAndCondition: UpdateAndCondition
) extends AttributesSummation {
  override val attributesSources: Seq[HasAttributes] = Seq(updateAndCondition)

  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def this(
    tableName: String,
    key: DynamoObject,
    updateExpression: UpdateExpression,
    condition: Option[RequestCondition]
  ) = this(tableName, key, UpdateAndCondition(updateExpression, condition))
  @deprecated("Use `updateAndCondition.update` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def updateExpression: UpdateExpression = updateAndCondition.update
  @deprecated("Use `updateAndCondition.condition` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def condition: Option[RequestCondition] = updateAndCondition.condition
}

case class TransactDeleteItem(
  tableName: String,
  key: DynamoObject,
  condition: Option[RequestCondition]
) extends TransactPunk with AttributesSummation {
  override val attributesSources: Seq[HasAttributes] = condition.toSeq
}
case class TransactConditionCheck(
  tableName: String,
  key: DynamoObject,
  condition: RequestCondition
) extends TransactPunk with AttributesSummation {
  override val attributesSources: Seq[HasAttributes] = Seq(condition)
}

case class ScanamoTransactWriteRequest(
  putItems: Seq[TransactPutItem],
  updateItems: Seq[TransactUpdateItem],
  deleteItems: Seq[TransactDeleteItem],
  conditionCheck: Seq[TransactConditionCheck]
)
