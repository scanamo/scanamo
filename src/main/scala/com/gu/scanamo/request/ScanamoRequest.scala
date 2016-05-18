package com.gu.scanamo.request

import com.amazonaws.services.dynamodbv2.model.AttributeValue

case class ScanamoPutRequest(
  tableName: String,
  item: java.util.Map[String, AttributeValue],
  condition: Option[RequestCondition]
)

case class ScanamoDeleteRequest(
  tableName: String,
  key: java.util.Map[String, AttributeValue],
  condition: Option[RequestCondition]
)

case class RequestCondition(
  expression: String,
  attributeNames: Map[String, String],
  attributeValues: Option[Map[String, AttributeValue]]
)
