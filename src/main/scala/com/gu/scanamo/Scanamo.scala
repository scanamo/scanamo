package com.gu.scanamo

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import collection.convert.decorateAsJava._

/**
  * Scanamo provides a simplified interface for reading and writing case classes to DynamoDB
  *
  * The examples in method documentation assume the following table has been created:
  * {{{
  * >>> import com.amazonaws.services.dynamodbv2.model._
  * >>> val client = LocalDynamoDB.client()
  * >>> val createTableResult = LocalDynamoDB.createTable(client, "farmers", List("name" -> ScalarAttributeType.S), List("name" -> KeyType.HASH))
  * }}}
  */
object Scanamo {
  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
    * >>> Scanamo.get[String, Farmer](client)("farmers")("name" -> "McDonald")
    * Some(Valid(Farmer(McDonald,156,Farm(List(sheep, cow)))))
    * }}}
    */
  def put[T](client: AmazonDynamoDB)(tableName: String)(item: T)(implicit f: DynamoFormat[T]): PutItemResult =
    client.putItem(putRequest(tableName)(item))

  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("Maggot", 75L, Farm(List("dog"))))
    * >>> Scanamo.get[String, Farmer](client)("farmers")("name" -> "Maggot")
    * Some(Valid(Farmer(Maggot,75,Farm(List(dog)))))
    * }}}
    */
  def get[K, T](client: AmazonDynamoDB)(tableName: String)(key: (String, K)*)
    (implicit fk: DynamoFormat[K], ft: DynamoFormat[T]): Option[ValidatedNel[DynamoReadError, T]] =
    from(client.getItem(getRequest(tableName)(key: _*)))

  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("McGregor", 62L, Farm(List("rabbit"))))
    * >>> val deleteResult = Scanamo.delete[String, Farmer](client)("farmers")("name" -> "McGregor")
    * >>> Scanamo.get[String, Farmer](client)("farmers")("name" -> "McGregor")
    * None
    * }}}
    */
  def delete[K, T](client: AmazonDynamoDB)(tableName: String)(key: (String, K)*)
    (implicit fk: DynamoFormat[K], ft: DynamoFormat[T]): DeleteItemResult =
    client.deleteItem(deleteRequest(tableName)(key: _*))

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> (m: Map[String, Int], tableName: String) =>
    *     |   val putRequest = Scanamo.putRequest(tableName)(m)
    *     |   putRequest.getTableName == tableName &&
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
    *     |   getRequest.getTableName == tableName &&
    *     |   getRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    */
  def getRequest[K](tableName: String)(key: (String, K)*)(implicit fk: DynamoFormat[K]): GetItemRequest =
    new GetItemRequest().withTableName(tableName).withKey(Map(key: _*).mapValues(fk.write).asJava)

  /**
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val deleteRequest = Scanamo.deleteRequest(tableName)(keyName -> keyValue)
    *     |   deleteRequest.getTableName == tableName &&
    *     |   deleteRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    */
  def deleteRequest[K](tableName: String)(key: (String, K)*)(implicit fk: DynamoFormat[K]): DeleteItemRequest =
    new DeleteItemRequest().withTableName(tableName).withKey(Map(key: _*).mapValues(fk.write).asJava)

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> (m: Map[String, Int]) =>
    *     |   Scanamo.from[Map[String, Int]](
    *     |     new GetItemResult().withItem(m.mapValues(i => new AttributeValue().withN(i.toString)).asJava)
    *     |   ) == Some(cats.data.Validated.valid(m))
    * }}}
    */
  def from[T](result: GetItemResult)(implicit f: DynamoFormat[T]): Option[ValidatedNel[DynamoReadError, T]] =
    Option(result.getItem).map(i => f.read(new AttributeValue().withM(i)))
}
