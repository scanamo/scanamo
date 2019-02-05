package org.scanamo
import scala.collection.JavaConverters._

object LocalDynamoDB {

  import com.amazonaws.services.{dynamodbv2 => dynamo_v1}

  object v1
      extends LocalDBBase[
        dynamo_v1.AmazonDynamoDB,
        dynamo_v1.AmazonDynamoDBAsync,
        dynamo_v1.model.ScalarAttributeType,
        dynamo_v1.model.CreateTableResult,
        dynamo_v1.model.DeleteTableResult
      ] {

    private val creds = new com.amazonaws.auth.AWSStaticCredentialsProvider(new com.amazonaws.auth.BasicAWSCredentials("dummy", "credentials"))

    def clientAsync(): dynamo_v1.AmazonDynamoDBAsync =
      dynamo_v1.AmazonDynamoDBAsyncClient
        .asyncBuilder()
        .withCredentials(creds)
        .withEndpointConfiguration(new com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration("http://localhost:8042", ""))
        .withClientConfiguration(new com.amazonaws.ClientConfiguration().withClientExecutionTimeout(50000).withRequestTimeout(5000))
        .build()

    override def clientSync(): dynamo_v1.AmazonDynamoDB =
      dynamo_v1.AmazonDynamoDBClient
        .builder()
        .withCredentials(creds)
        .withEndpointConfiguration(new com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration("http://localhost:8042", ""))
        .withClientConfiguration(new com.amazonaws.ClientConfiguration().withClientExecutionTimeout(50000).withRequestTimeout(5000))
        .build()

    override def createTable(
      client: dynamo_v1.AmazonDynamoDB
    )(tableName: String)(attributes: (Symbol, dynamo_v1.model.ScalarAttributeType)*): dynamo_v1.model.CreateTableResult =
      client.createTable(
        attributeDefinitions(attributes),
        tableName,
        keySchema(attributes),
        arbitraryThroughputThatIsIgnoredByDynamoDBLocal
      )

    override def createTableWithIndex(
      client: dynamo_v1.AmazonDynamoDB,
      tableName: String,
      secondaryIndexName: String,
      primaryIndexAttributes: List[(Symbol, dynamo_v1.model.ScalarAttributeType)],
      secondaryIndexAttributes: List[(Symbol, dynamo_v1.model.ScalarAttributeType)]
    ): dynamo_v1.model.CreateTableResult =
      client.createTable(
        new dynamo_v1.model.CreateTableRequest()
          .withTableName(tableName)
          .withAttributeDefinitions(
            attributeDefinitions(primaryIndexAttributes ++ (secondaryIndexAttributes diff primaryIndexAttributes))
          )
          .withKeySchema(keySchema(primaryIndexAttributes))
          .withProvisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
          .withGlobalSecondaryIndexes(
            new dynamo_v1.model.GlobalSecondaryIndex()
              .withIndexName(secondaryIndexName)
              .withKeySchema(keySchema(secondaryIndexAttributes))
              .withProvisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
              .withProjection(new dynamo_v1.model.Projection().withProjectionType(dynamo_v1.model.ProjectionType.ALL))
          )
      )

    def deleteTable(client: dynamo_v1.AmazonDynamoDB)(tableName: String): dynamo_v1.model.DeleteTableResult =
      client.deleteTable(tableName)

    private def keySchema(attributes: Seq[(Symbol, dynamo_v1.model.ScalarAttributeType)]) = {
      val hashKeyWithType :: rangeKeyWithType = attributes.toList
      val keySchemas = hashKeyWithType._1 -> dynamo_v1.model.KeyType.HASH :: rangeKeyWithType.map(_._1 -> dynamo_v1.model.KeyType.RANGE)
      keySchemas.map { case (symbol, keyType) => new dynamo_v1.model.KeySchemaElement(symbol.name, keyType) }.asJava
    }

    private def attributeDefinitions(attributes: Seq[(Symbol, dynamo_v1.model.ScalarAttributeType)]) =
      attributes.map { case (symbol, attributeType) => new dynamo_v1.model.AttributeDefinition(symbol.name, attributeType) }.asJava

    private val arbitraryThroughputThatIsIgnoredByDynamoDBLocal = new dynamo_v1.model.ProvisionedThroughput(1L, 1L)
  }

  import software.amazon.awssdk.services.{dynamodb => dynamo_v2}

  object v2
      extends LocalDBBase[
        dynamo_v2.DynamoDbClient,
        dynamo_v2.DynamoDbAsyncClient,
        dynamo_v2.model.ScalarAttributeType,
        dynamo_v2.model.CreateTableResponse,
        dynamo_v2.model.DeleteTableResponse
      ] {

    private val cfgs = software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
      .builder()
      .apiCallTimeout(java.time.Duration.ofSeconds(50L))
      .apiCallAttemptTimeout(java.time.Duration.ofSeconds(5L))
      .build()

    override def clientSync(): dynamo_v2.DynamoDbClient = {
      import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}

      dynamo_v2.DynamoDbClient
        .builder()
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "credentials")))
        .overrideConfiguration(cfgs)
        .endpointOverride(java.net.URI.create("http://localhost:8042"))
        .build()
    }


    override def clientAsync(): dynamo_v2.DynamoDbAsyncClient = {
      import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}

      dynamo_v2.DynamoDbAsyncClient
        .builder()
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "credentials")))
        .overrideConfiguration(cfgs)
        .endpointOverride(java.net.URI.create("http://localhost:8042"))
        .build()
    }


    override def createTable(
      client: dynamo_v2.DynamoDbClient
    )(tableName: String)(
      attributes: (Symbol, dynamo_v2.model.ScalarAttributeType)*
    ): dynamo_v2.model.CreateTableResponse = {
      val req = dynamo_v2.model.CreateTableRequest
        .builder()
        .attributeDefinitions(attributeDefinitions(attributes))
        .keySchema(keySchema(attributes))
        .tableName(tableName)
        .provisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
        .build()

      client.createTable(req)
    }

    override def createTableWithIndex(
      client: dynamo_v2.DynamoDbClient,
      tableName: String,
      secondaryIndexName: String,
      primaryIndexAttributes: List[
        (Symbol, dynamo_v2.model.ScalarAttributeType)
      ],
      secondaryIndexAttributes: List[
        (Symbol, dynamo_v2.model.ScalarAttributeType)
      ]
    ): dynamo_v2.model.CreateTableResponse =
      client.createTable(
        dynamo_v2.model.CreateTableRequest
          .builder()
          .tableName(tableName)
          .attributeDefinitions(
            attributeDefinitions(primaryIndexAttributes ++ (secondaryIndexAttributes diff primaryIndexAttributes))
          )
          .keySchema(keySchema(primaryIndexAttributes))
          .provisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
          .globalSecondaryIndexes(
            dynamo_v2.model.GlobalSecondaryIndex
              .builder()
              .indexName(secondaryIndexName)
              .keySchema(keySchema(secondaryIndexAttributes))
              .provisionedThroughput(arbitraryThroughputThatIsIgnoredByDynamoDBLocal)
              .projection(dynamo_v2.model.Projection.builder().projectionType(dynamo_v2.model.ProjectionType.ALL).build())
              .build()
          )
          .build()
      )

    override def deleteTable(client: dynamo_v2.DynamoDbClient)(
      tableName: String
    ): dynamo_v2.model.DeleteTableResponse =
      client.deleteTable(
        dynamo_v2.model.DeleteTableRequest.builder().tableName(tableName).build()
      )

    private def keySchema(attributes: Seq[(Symbol, dynamo_v2.model.ScalarAttributeType)]) = {
      val hashKeyWithType :: rangeKeyWithType = attributes.toList
      val keySchemas = hashKeyWithType._1 -> dynamo_v2.model.KeyType.HASH :: rangeKeyWithType.map(_._1 -> dynamo_v2.model.KeyType.RANGE)
      keySchemas.map {
        case (symbol, keyType) => dynamo_v2.model.KeySchemaElement.builder().attributeName(symbol.name).keyType(keyType).build()
      }.asJava
    }

    private def attributeDefinitions(attributes: Seq[(Symbol, dynamo_v2.model.ScalarAttributeType)]) =
      attributes.map {
        case (symbol, attributeType) =>
          dynamo_v2.model.AttributeDefinition.builder.attributeName(symbol.name).attributeType(attributeType).build()
      }.asJava

    private val arbitraryThroughputThatIsIgnoredByDynamoDBLocal =
      dynamo_v2.model.ProvisionedThroughput.builder.readCapacityUnits(1L).writeCapacityUnits(1L).build()
  }

}
