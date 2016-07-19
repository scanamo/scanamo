package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.query.{Query, UniqueKey, UniqueKeys}
import com.gu.scanamo.request.{ScanamoDeleteRequest, ScanamoPutRequest, ScanamoUpdateRequest}
import com.gu.scanamo.update.UpdateExpression

import scala.collection.convert.decorateAll._

private object Requests {

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (m: Map[String, Int], tableName: String) =>
    *     |   val putRequest = Requests.putRequest(tableName)(m)
    *     |   putRequest.tableName == tableName &&
    *     |   putRequest.item == m.mapValues(i => new AttributeValue().withN(i.toString)).asJava
    * }}}
    */
  def putRequest[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): ScanamoPutRequest =
    ScanamoPutRequest(tableName, f.write(item).getM, None)

  def batchPutRequest[T](tableName: String)(items: Set[T])(implicit f: DynamoFormat[T]): BatchWriteItemRequest =
    new BatchWriteItemRequest().withRequestItems(Map(tableName -> items.toList.map(i =>
      new WriteRequest().withPutRequest(new PutRequest().withItem(f.write(i).getM))
    ).asJava).asJava)

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> import com.gu.scanamo.syntax._
    *
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val getRequest = Requests.getRequest(tableName)(Symbol(keyName) -> keyValue)
    *     |   getRequest.getTableName == tableName &&
    *     |   getRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    * }}}
    */
  def getRequest[T](tableName: String)(key: UniqueKey[_]): GetItemRequest =
    new GetItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava)

  def batchGetRequest[K: DynamoFormat](tableName: String)(keys: UniqueKeys[_]): BatchGetItemRequest =
    new BatchGetItemRequest().withRequestItems(Map(tableName ->
      new KeysAndAttributes().withKeys(keys.asAVMap.map(_.asJava).asJava)
    ).asJava)

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> import com.gu.scanamo.syntax._
    *
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val deleteRequest = Requests.deleteRequest(tableName)(Symbol(keyName) -> keyValue)
    *     |   deleteRequest.tableName == tableName &&
    *     |   deleteRequest.key == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    * }}}
    */
  def deleteRequest[T](tableName: String)(key: UniqueKey[_]): ScanamoDeleteRequest =
    ScanamoDeleteRequest(tableName = tableName, key = key.asAVMap.asJava, None)

  def queryRequest[T](tableName: String)(query: Query[_]): QueryRequest = {
    query(new QueryRequest().withTableName(tableName))
  }

  def updateRequest[T](tableName: String)(key: UniqueKey[_])(expression: T)(implicit update: UpdateExpression[T]) =
    ScanamoUpdateRequest(tableName = tableName, key = key.asAVMap.asJava,
      update.expression(expression), update.attributeNames(expression), update.attributeValues(expression), None)
}
