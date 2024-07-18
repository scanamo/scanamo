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

import org.scanamo.query.{ Condition, Query }
import org.scanamo.update.UpdateExpression
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
  updateExpression: String,
  attributeNames: Map[String, String],
  dynamoValues: DynamoObject,
  addEmptyList: Boolean,
  condition: Option[RequestCondition]
)

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
  attributeNames: Map[String, String],
  dynamoValues: Option[DynamoObject]
)

case class TransactPutItem(
  tableName: String,
  item: DynamoValue,
  condition: Option[RequestCondition]
)

case class TransactUpdateItem(
  tableName: String,
  key: DynamoObject,
  updateExpression: UpdateExpression,
  condition: Option[RequestCondition]
)

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
