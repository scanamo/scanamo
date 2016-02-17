package com.gu.scanamo

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import collection.convert.decorateAsJava._

/**
  * {{{
  * >>> import com.amazonaws.services.dynamodbv2._
  * >>> import com.amazonaws.services.dynamodbv2.model._
  * >>> import collection.convert.decorateAsJava._
  * >>> val client = { val c = new AmazonDynamoDBClient(); c.setEndpoint("http://localhost:8000"); c }
  * >>> val tableResult = client.createTable(
  * ...   List(new AttributeDefinition("name", ScalarAttributeType.S)).asJava, "farmers", List(new KeySchemaElement("name", KeyType.HASH)).asJava,
  * ...   new ProvisionedThroughput(1, 1)
  * ... )
  * >>> case class Farm(animals: List[String])
  * >>> case class Farmer(name: String, age: Long, farm: Farm)
  * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
  * >>> Scanamo.get[String, Farmer](client)("farmers")("name" -> "McDonald")
  * Valid(Farmer(McDonald,156,Farm(List(sheep, cow))))
  * }}}
  */
object Scanamo {
  def put[T](client: AmazonDynamoDB)(tableName: String)(item: T)(implicit f: DynamoFormat[T]): PutItemResult =
    client.putItem(putRequest(tableName)(item))

  def get[K, T](client: AmazonDynamoDB)(tableName: String)(key: (String, K)*)
    (implicit fk: DynamoFormat[K], ft: DynamoFormat[T]): ValidatedNel[DynamoReadError, T] =
    from(client.getItem(getRequest(tableName)(key: _*)))

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> (m: Map[String, Int], tableName: String) =>
    *     |   val putRequest = Scanamo.putRequest(tableName)(m)
    *     |   putRequest.getTableName == tableName
    *     |   putRequest.getItem == m.mapValues(i => new AttributeValue().withN(i.toString)).asJava
    * }}}
    */
  def putRequest[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): PutItemRequest =
    new PutItemRequest().withTableName(tableName).withItem(f.write(item).getM)


  /**
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val getRequest = Scanamo.getRequest(tableName)(keyName -> keyValue)
    *     |   getRequest.getTableName == tableName
    *     |   getRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    */
  def getRequest[K](tableName: String)(key: (String, K)*)(implicit fk: DynamoFormat[K]): GetItemRequest =
    new GetItemRequest().withTableName(tableName).withKey(Map(key: _*).mapValues(fk.write).asJava)

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> (m: Map[String, Int]) =>
    *     |   Scanamo.from[Map[String, Int]](
    *     |     new GetItemResult().withItem(m.mapValues(i => new AttributeValue().withN(i.toString)).asJava)
    *     |   ) == cats.data.Validated.valid(m)
    * }}}
    */
  def from[T](result: GetItemResult)(implicit f: DynamoFormat[T]): ValidatedNel[DynamoReadError, T] =
    f.read(new AttributeValue().withM(result.getItem))
}
