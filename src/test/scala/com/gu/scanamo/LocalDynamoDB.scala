package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClient, model}

import collection.convert.decorateAsJava._

object LocalDynamoDB {
  def client() = {
    val c = new AmazonDynamoDBClient(new com.amazonaws.auth.BasicAWSCredentials("key", "secret"))
    c.setEndpoint("http://localhost:8000")
    c
  }
  def createTable(client: AmazonDynamoDB)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*) = {
    val hashKeyWithType :: rangeKeyWithType = attributeDefinitions.toList
    val keySchemas = hashKeyWithType._1 -> KeyType.HASH :: rangeKeyWithType.map(_._1 -> KeyType.RANGE)
    client.createTable(
      attributeDefinitions.map{ case (symbol, attributeType) => new AttributeDefinition(symbol.name, attributeType)}.asJava,
      tableName,
      keySchemas.map{ case (symbol, keyType) => new KeySchemaElement(symbol.name, keyType)}.asJava,
      new ProvisionedThroughput(1L, 1L)
    )
  }
}
