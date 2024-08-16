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

case class ScanamoPutRequest(
  tableName: String,
  item: DynamoValue,
  condition: Option[RequestCondition],
  ret: PutReturn
)

case class ScanamoDeleteRequest(
  tableName: String,
  key: DynamoObject,
  condition: Option[RequestCondition],
  ret: DeleteReturn
)

case class ScanamoUpdateRequest(
  tableName: String,
  key: DynamoObject,
  updateAndCondition: UpdateAndCondition
) {
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
)

case class ScanamoQueryRequest(
  tableName: String,
  index: Option[String],
  query: Query[_],
  options: ScanamoQueryOptions
)

case class ScanamoQueryOptions(
  consistent: Boolean,
  ascending: Boolean,
  limit: Option[Int],
  exclusiveStartKey: Option[DynamoObject],
  filter: Option[Condition[_]]
)
object ScanamoQueryOptions {
  val default = ScanamoQueryOptions(false, true, None, None, None)
}

case class RequestCondition(
  expression: String,
  attributes: AttributeNamesAndValues
) {
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

case class TransactPutItem(
  tableName: String,
  item: DynamoValue,
  condition: Option[RequestCondition]
)

case class TransactUpdateItem(
  tableName: String,
  key: DynamoObject,
  updateAndCondition: UpdateAndCondition
) {
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
)
case class TransactConditionCheck(
  tableName: String,
  key: DynamoObject,
  condition: RequestCondition
)

case class ScanamoTransactWriteRequest(
  putItems: Seq[TransactPutItem],
  updateItems: Seq[TransactUpdateItem],
  deleteItems: Seq[TransactDeleteItem],
  conditionCheck: Seq[TransactConditionCheck]
)
