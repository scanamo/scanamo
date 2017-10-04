package com.gu.scanamo.request

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ReturnValue}
import com.gu.scanamo.query.{Condition, Query}

case class ScanamoPutRequest(
  tableName: String,
  item: AttributeValue,
  condition: Option[RequestCondition],
  returnValue: Option[ReturnValue]
)

case class ScanamoDeleteRequest(
  tableName: String,
  key: Map[String, AttributeValue],
  condition: Option[RequestCondition],
  returnValue: Option[ReturnValue]
)

case class ScanamoUpdateRequest(
  tableName: String,
  key: Map[String, AttributeValue],
  updateExpression: String,
  attributeNames: Map[String, String],
  attributeValues: Map[String, AttributeValue],
  condition: Option[RequestCondition],
  returnValue: Option[ReturnValue]
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
  limit: Option[Int],
  exclusiveStartKey: Option[Map[String, AttributeValue]],
  filter: Option[Condition[_]]
)
object ScanamoQueryOptions {
  val default = ScanamoQueryOptions(false, None, None, None)
}

case class RequestCondition(
  expression: String,
  attributeNames: Map[String, String],
  attributeValues: Option[Map[String, AttributeValue]]
)
