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

  def batchPutRequest[T](tableName: String)(items: List[T])(implicit f: DynamoFormat[T]): BatchWriteItemRequest =
    new BatchWriteItemRequest().withRequestItems(Map(tableName -> items.map(i =>
      new WriteRequest().withPutRequest(new PutRequest().withItem(f.write(i).getM))
    ).asJava).asJava)

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> import DynamoKeyCondition.syntax._
    *
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val getRequest = ScanamoRequest.getRequest(tableName)(Symbol(keyName) === keyValue)
    *     |   getRequest.getTableName == tableName &&
    *     |   getRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
*     }}}
    */
  def getRequest[T](tableName: String)(key: AttributeValueMap): GetItemRequest =
    new GetItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava)

  def batchGetRequest[K](tableName: String)(keys: (Symbol, List[K]))(implicit fk: DynamoFormat[K]): BatchGetItemRequest =
    new BatchGetItemRequest().withRequestItems(Map(tableName ->
      new KeysAndAttributes().withKeys((keys._2.map(k => Map(keys._1.name -> fk.write(k)).asJava).asJava))
    ).asJava)

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> import DynamoKeyCondition.syntax._
    *
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val deleteRequest = ScanamoRequest.deleteRequest(tableName)(Symbol(keyName) === keyValue)
    *     |   deleteRequest.getTableName == tableName &&
    *     |   deleteRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    * }}}
    */
  def deleteRequest[T](tableName: String)(key: AttributeValueMap): DeleteItemRequest =
    new DeleteItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava)

  def queryRequest[T](tableName: String)(query: Query): QueryRequest = {
    query(new QueryRequest().withTableName(tableName))
  }
}
