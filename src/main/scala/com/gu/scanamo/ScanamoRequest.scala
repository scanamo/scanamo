package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model._

import scala.collection.convert.decorateAll._

object ScanamoRequest {

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (m: Map[String, Int], tableName: String) =>
    *     |   val putRequest = ScanamoRequest.putRequest(tableName)(m)
    *     |   putRequest.getTableName == tableName &&
    *     |   putRequest.getItem == m.mapValues(i => new AttributeValue().withN(i.toString)).asJava
    * }}}
    */
  def putRequest[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): PutItemRequest =
    new PutItemRequest().withTableName(tableName).withItem(f.write(item).getM)


  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val getRequest = ScanamoRequest.getRequest(tableName)(keyName -> keyValue)
    *     |   getRequest.getTableName == tableName &&
    *     |   getRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
*     }}}
    */
  def getRequest[K](tableName: String)(key: (String, K)*)(implicit fk: DynamoFormat[K]): GetItemRequest =
    new GetItemRequest().withTableName(tableName).withKey(asAVMap(key: _*))

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val deleteRequest = ScanamoRequest.deleteRequest(tableName)(keyName -> keyValue)
    *     |   deleteRequest.getTableName == tableName &&
    *     |   deleteRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    * }}}
    */
  def deleteRequest[K](tableName: String)(key: (String, K)*)(implicit fk: DynamoFormat[K]): DeleteItemRequest =
    new DeleteItemRequest().withTableName(tableName).withKey(asAVMap(key: _*))

  def queryRequest[K](tableName: String)(key: (String, K))(implicit fk: DynamoFormat[K]): QueryRequest = {
    val (k, v) = key
    new QueryRequest().withTableName(tableName)
      .withKeyConditionExpression(s"#K = :$k")
      .withExpressionAttributeNames(Map("#K" -> k).asJava)
      .withExpressionAttributeValues(asAVMap(s":$k" -> v))
  }

  private def asAVMap[K](kvs: (String, K)*)(implicit fk: DynamoFormat[K]) =
    Map(kvs: _*).mapValues(fk.write).asJava
}
