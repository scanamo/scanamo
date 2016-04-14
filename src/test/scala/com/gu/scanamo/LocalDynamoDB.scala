package com.gu.scanamo

import com.amazonaws.services.dynamodbv2._
import com.amazonaws.services.dynamodbv2.model._

import scala.collection.convert.decorateAsJava._

object LocalDynamoDB {
  def client() = {
    val c = new AmazonDynamoDBAsyncClient(new com.amazonaws.auth.BasicAWSCredentials("key", "secret"))
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

  def withTable[T](client: AmazonDynamoDB)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
        thunk: => T
  ): T = {
    createTable(client)(tableName)(attributeDefinitions: _*)
    val res = thunk
    client.deleteTable(tableName)
    res
  }

  def usingTable[T](client: AmazonDynamoDB)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): Unit = {
    withTable(client)(tableName)(attributeDefinitions: _*)(thunk)
    ()
  }

  def withTableWithSecondaryIndex[T](client: AmazonDynamoDB)(tableName: String, secondaryIndexName: String)
    (primaryIndexAttributes: (Symbol, ScalarAttributeType)*)(secondaryIndexAttributes: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): T = {
    def keySchema(attributes: List[(Symbol, ScalarAttributeType)]) = {
      val hashKeyWithType :: rangeKeyWithType = attributes.toList
      val keySchemas = hashKeyWithType._1 -> KeyType.HASH :: rangeKeyWithType.map(_._1 -> KeyType.RANGE)
      keySchemas.map{ case (symbol, keyType) => new KeySchemaElement(symbol.name, keyType)}.asJava
    }
    client.createTable(
      new CreateTableRequest().withTableName(tableName)
          .withAttributeDefinitions(
            (primaryIndexAttributes ++ secondaryIndexAttributes)
              .map{ case (symbol, attributeType) => new AttributeDefinition(symbol.name, attributeType)}.asJava
          )
          .withKeySchema(keySchema(primaryIndexAttributes.toList))
          .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
          .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
            .withIndexName(secondaryIndexName)
            .withKeySchema(keySchema(secondaryIndexAttributes.toList))
            .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
            .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
          )
    )
    val res = thunk
    client.deleteTable(tableName)
    res
  }
}
