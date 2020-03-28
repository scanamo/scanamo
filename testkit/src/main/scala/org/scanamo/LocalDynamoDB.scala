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

package org.scanamo

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2._
import com.amazonaws.services.dynamodbv2.model._

import scala.collection.JavaConverters._

object LocalDynamoDB {
  def client(port: Int = 8042): AmazonDynamoDBAsync =
    AmazonDynamoDBAsyncClient
      .asyncBuilder()
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("dummy", "credentials")))
      .withEndpointConfiguration(new EndpointConfiguration(s"http://localhost:$port", ""))
      .withClientConfiguration(new ClientConfiguration().withClientExecutionTimeout(50000).withRequestTimeout(5000))
      .build()

  def createTable(client: AmazonDynamoDB)(tableName: String)(attributes: (String, ScalarAttributeType)*) =
    client.createTable(
      attributeDefinitions(attributes),
      tableName,
      keySchema(attributes),
      arbitraryThroughputThatIsIgnoredByDynamoDBLocal
    )

  def createTableWithIndex(
    client: AmazonDynamoDB,
    tableName: String,
    secondaryIndexName: String,
    primaryIndexAttributes: List[(String, ScalarAttributeType)],
    secondaryIndexAttributes: List[(String, ScalarAttributeType)]
  ) =
    client.createTable(
      new CreateTableRequest()
        .withTableName(tableName)
        .withAttributeDefinitions(
          attributeDefinitions(primaryIndexAttributes ++ (secondaryIndexAttributes diff primaryIndexAttributes))
        )
        .withKeySchema(keySchema(primaryIndexAttributes))
        .withProvisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
        .withGlobalSecondaryIndexes(
          new GlobalSecondaryIndex()
            .withIndexName(secondaryIndexName)
            .withKeySchema(keySchema(secondaryIndexAttributes))
            .withProvisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
            .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
        )
    )

  def deleteTable(client: AmazonDynamoDB)(tableName: String) =
    client.deleteTable(tableName)

  def withTable[T](client: AmazonDynamoDB)(tableName: String)(attributeDefinitions: (String, ScalarAttributeType)*)(
    thunk: => T
  ): T = {
    createTable(client)(tableName)(attributeDefinitions: _*)
    val res =
      try {
        thunk
      } finally {
        client.deleteTable(tableName)
        ()
      }
    res
  }

  def withRandomTable[T](client: AmazonDynamoDB)(attributeDefinitions: (String, ScalarAttributeType)*)(
    thunk: String => T
  ): T = {
    var created: Boolean = false
    var tableName: String = null
    while (!created) {
      try {
        tableName = java.util.UUID.randomUUID.toString
        createTable(client)(tableName)(attributeDefinitions: _*)
        created = true
      } catch {
        case _: ResourceInUseException =>
      }
    }

    val res =
      try {
        thunk(tableName)
      } finally {
        client.deleteTable(tableName)
        ()
      }
    res
  }

  def usingTable[T](client: AmazonDynamoDB)(tableName: String)(attributeDefinitions: (String, ScalarAttributeType)*)(
    thunk: => T
  ): Unit = {
    withTable(client)(tableName)(attributeDefinitions: _*)(thunk)
    ()
  }

  def usingRandomTable[T](client: AmazonDynamoDB)(attributeDefinitions: (String, ScalarAttributeType)*)(
    thunk: String => T
  ): Unit = {
    withRandomTable(client)(attributeDefinitions: _*)(thunk)
    ()
  }

  def withTableWithSecondaryIndex[T](client: AmazonDynamoDB)(tableName: String, secondaryIndexName: String)(
    primaryIndexAttributes: (String, ScalarAttributeType)*
  )(secondaryIndexAttributes: (String, ScalarAttributeType)*)(
    thunk: => T
  ): T = {
    createTableWithIndex(
      client,
      tableName,
      secondaryIndexName,
      primaryIndexAttributes.toList,
      secondaryIndexAttributes.toList
    )
    val res =
      try {
        thunk
      } finally {
        client.deleteTable(tableName)
        ()
      }
    res
  }

  def withRandomTableWithSecondaryIndex[T](
    client: AmazonDynamoDB
  )(primaryIndexAttributes: (String, ScalarAttributeType)*)(secondaryIndexAttributes: (String, ScalarAttributeType)*)(
    thunk: (String, String) => T
  ): T = {
    var tableName: String = null
    var indexName: String = null
    var created: Boolean = false
    while (!created) {
      try {
        tableName = java.util.UUID.randomUUID.toString
        indexName = java.util.UUID.randomUUID.toString
        createTableWithIndex(
          client,
          tableName,
          indexName,
          primaryIndexAttributes.toList,
          secondaryIndexAttributes.toList
        )
        created = true
      } catch {
        case _: ResourceInUseException =>
      }
    }

    val res =
      try {
        thunk(tableName, indexName)
      } finally {
        client.deleteTable(tableName)
        ()
      }
    res
  }

  private def keySchema(attributes: Seq[(String, ScalarAttributeType)]) = {
    val hashKeyWithType :: rangeKeyWithType = attributes.toList
    val keySchemas = hashKeyWithType._1 -> KeyType.HASH :: rangeKeyWithType.map(_._1 -> KeyType.RANGE)
    keySchemas.map { case (symbol, keyType) => new KeySchemaElement(symbol, keyType) }.asJava
  }

  private def attributeDefinitions(attributes: Seq[(String, ScalarAttributeType)]) =
    attributes.map { case (symbol, attributeType) => new AttributeDefinition(symbol, attributeType) }.asJava

  private val arbitraryThroughputThatIsIgnoredByDynamoDBLocal = new ProvisionedThroughput(1L, 1L)
}
