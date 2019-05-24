package org.scanamo.request

import org.scanamo.{ DynamoObject, DynamoValue }
import org.scanamo.query.{ Condition, Query }

case class ScanamoPutRequest(
  tableName: String,
  item: DynamoValue,
  condition: Option[RequestCondition]
)

case class ScanamoDeleteRequest(
  tableName: String,
  key: DynamoObject,
  condition: Option[RequestCondition]
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
