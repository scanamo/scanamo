package com.gu.scanamo.request

import com.amazonaws.services.dynamodbv2.model.AttributeValue

case class ScanamoPutRequest(
  tableName: String,
  item: AttributeValue,
  condition: Option[RequestCondition]
)

case class ScanamoDeleteRequest(
  tableName: String,
  key: Map[String, AttributeValue],
  condition: Option[RequestCondition]
)

case class ScanamoUpdateRequest(
  tableName: String,
  key: Map[String, AttributeValue],
  updateExpression: String,
  attributeNames: Map[String, String],
  attributeValues: Map[String, AttributeValue],
  condition: Option[RequestCondition]
)

case class ScanamoScanRequest(
  tableName: String,
  index: Option[String],
  options: ScanamoQueryOptions
)

case class ScanamoQueryOptions(
  consistent: Boolean,
  limit: Option[Int],
  exclusiveStartKey: Option[Map[String, AttributeValue]]
)
object ScanamoQueryOptions {
  val default = ScanamoQueryOptions(false, None, None)
}

case class RequestCondition(
  expression: String,
  attributeNames: Map[String, String],
  attributeValues: Option[Map[String, AttributeValue]]
)
