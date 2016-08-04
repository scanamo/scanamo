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

case class RequestCondition(
  expression: String,
  attributeNames: Map[String, String],
  attributeValues: Option[Map[String, AttributeValue]]
)
