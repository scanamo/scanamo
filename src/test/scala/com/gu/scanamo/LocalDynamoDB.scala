package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClient}

import collection.convert.decorateAsJava._

object LocalDynamoDB {
  def client() = {
    val c = new AmazonDynamoDBClient(new com.amazonaws.auth.BasicAWSCredentials("key", "secret"))
    c.setEndpoint("http://localhost:8000")
    c
  }
  def createTable(
    client: AmazonDynamoDB,
    tableName: String,
    attributeDefinitions: List[(String, ScalarAttributeType)],
    keySchemas: List[(String, KeyType)]
  ) = {
    client.createTable(
      attributeDefinitions.map{ case (name, attributeType) => new AttributeDefinition(name, attributeType)}.asJava,
      tableName,
      keySchemas.map{ case (name, keyType) => new KeySchemaElement(name, keyType)}.asJava,
      new ProvisionedThroughput(1L, 1L)
    )
  }
}
