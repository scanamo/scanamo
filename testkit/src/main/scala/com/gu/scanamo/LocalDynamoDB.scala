package org.scanamo

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.services.dynamodb.model._
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbClient}

import scala.collection.JavaConverters._
import scala.language.postfixOps

object LocalDynamoDB {
  def client(): DynamoDbAsyncClient = {

    val cfgs = ClientOverrideConfiguration.builder()
      .apiCallTimeout(java.time.Duration.ofSeconds(50L))
      .apiCallAttemptTimeout(java.time.Duration.ofSeconds(5L))
      .build()

    DynamoDbAsyncClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "credentials")))
      .overrideConfiguration(cfgs)
      .endpointOverride(java.net.URI.create("http://localhost:8042"))
      .build()

  }

  def createTable(client: DynamoDbClient)(tableName: String)(attributes: (Symbol, ScalarAttributeType)*): CreateTableRequest = {
    CreateTableRequest.builder()
      .attributeDefinitions(attributeDefinitions(attributes))
      .keySchema(keySchema(attributes))
      .tableName(tableName)
      .provisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
      .build()
  }


  def createTableWithIndex(
    client: DynamoDbClient,
    tableName: String,
    secondaryIndexName: String,
    primaryIndexAttributes: List[(Symbol, ScalarAttributeType)],
    secondaryIndexAttributes: List[(Symbol, ScalarAttributeType)]
  ): CreateTableResponse =
    client.createTable(
      CreateTableRequest.builder()
        .tableName(tableName)
        .attributeDefinitions(
          attributeDefinitions(primaryIndexAttributes ++ (secondaryIndexAttributes diff primaryIndexAttributes))
        )
        .keySchema(keySchema(primaryIndexAttributes))
        .provisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
        .globalSecondaryIndexes(
          GlobalSecondaryIndex.builder()
            .indexName(secondaryIndexName)
            .keySchema(keySchema(secondaryIndexAttributes))
            .provisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
            .build()
        )
        .build()
    )

  def deleteTable(client: DynamoDbClient)(tableName: String): DeleteTableResponse =
    client.deleteTable(
      DeleteTableRequest.builder().tableName(tableName).build()
    )

  def withTable[T](client: DynamoDbClient)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): T = {
    createTable(client)(tableName)(attributeDefinitions: _*)
    val res = try {
      thunk
    } finally {
      deleteTable(client)(tableName)
      ()
    }
    res
  }

  def withRandomTable[T](client: DynamoDbClient)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
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
        case e: ResourceInUseException =>
      }
    }

    val res = try {
      thunk(tableName)
    } finally {
      deleteTable(client)(tableName)
      ()
    }
    res
  }

  def usingTable[T](client: DynamoDbClient)(tableName: String)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): Unit = {
    withTable(client)(tableName)(attributeDefinitions: _*)(thunk)
    ()
  }

  def usingRandomTable[T](client: DynamoDbClient)(attributeDefinitions: (Symbol, ScalarAttributeType)*)(
    thunk: String => T
  ): Unit = {
    withRandomTable(client)(attributeDefinitions: _*)(thunk)
    ()
  }

  def withTableWithSecondaryIndex[T](client: DynamoDbClient)(tableName: String, secondaryIndexName: String)(
    primaryIndexAttributes: (Symbol, ScalarAttributeType)*
  )(secondaryIndexAttributes: (Symbol, ScalarAttributeType)*)(
    thunk: => T
  ): T = {
    createTableWithIndex(
      client,
      tableName,
      secondaryIndexName,
      primaryIndexAttributes.toList,
      secondaryIndexAttributes.toList
    )
    val res = try {
      thunk
    } finally {
      deleteTable(client)(tableName)
      ()
    }
    res
  }

  def withRandomTableWithSecondaryIndex[T](
    client: DynamoDbClient
  )(primaryIndexAttributes: (Symbol, ScalarAttributeType)*)(secondaryIndexAttributes: (Symbol, ScalarAttributeType)*)(
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
        case t: ResourceInUseException =>
      }
    }

    val res = try {
      thunk(tableName, indexName)
    } finally {
      deleteTable(client)(tableName)
      ()
    }
    res
  }

  private def keySchema(attributes: Seq[(Symbol, ScalarAttributeType)]) = {
    val hashKeyWithType :: rangeKeyWithType = attributes.toList
    val keySchemas = hashKeyWithType._1 -> KeyType.HASH :: rangeKeyWithType.map(_._1 -> KeyType.RANGE)
    keySchemas.map { case (symbol, keyType) => new KeySchemaElement(symbol.name, keyType) }.asJava
  }

  private def attributeDefinitions(attributes: Seq[(Symbol, ScalarAttributeType)]) =
    attributes.map { case (symbol, attributeType) => new AttributeDefinition(symbol.name, attributeType) }.asJava

  private val arbitraryThroughputThatIsIgnoredByDynamoDBLocal = new ProvisionedThroughput(1L, 1L)
}
