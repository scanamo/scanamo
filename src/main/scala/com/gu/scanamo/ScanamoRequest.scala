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
    *     |   val getRequest = ScanamoRequest.getRequest[Long](tableName)(Symbol(keyName) -> keyValue)
    *     |   getRequest.getTableName == tableName &&
    *     |   getRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    * }}}
    */
  def getRequest[HK](tableName: String)(hashkey: (Symbol, HK))(implicit fhk: DynamoFormat[HK]): GetItemRequest =
    new GetItemRequest().withTableName(tableName).withKey(asAVMap(hashkey))

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (hashKeyName: String, hashKeyValue: String, rangeKeyName: String, rangeKeyValue: Long, tableName: String) =>
    *     |   val getRequest = ScanamoRequest.getRequest[String, Long](tableName)(Symbol(hashKeyName) -> hashKeyValue, Symbol(rangeKeyName) -> rangeKeyValue)
    *     |   getRequest.getTableName == tableName &&
    *     |   getRequest.getKey == Map(hashKeyName -> new AttributeValue().withS(hashKeyValue), rangeKeyName -> new AttributeValue().withN(rangeKeyValue.toString)).asJava
    * }}}
    */
  def getRequest[HK, RK](tableName: String)(hashkey: (Symbol, HK), rangekey: (Symbol, RK))
    (implicit fhk: DynamoFormat[HK], frk: DynamoFormat[RK]): GetItemRequest =
    new GetItemRequest().withTableName(tableName).withKey(mergeAVMaps(asAVMap(hashkey), asAVMap(rangekey)))

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (keyName: String, keyValue: Long, tableName: String) =>
    *     |   val deleteRequest = ScanamoRequest.deleteRequest[Long](tableName)(Symbol(keyName) -> keyValue)
    *     |   deleteRequest.getTableName == tableName &&
    *     |   deleteRequest.getKey == Map(keyName -> new AttributeValue().withN(keyValue.toString)).asJava
    * }}}
    */
  def deleteRequest[HK](tableName: String)(hashkey: (Symbol, HK))(implicit fk: DynamoFormat[HK]): DeleteItemRequest =
    new DeleteItemRequest().withTableName(tableName).withKey(asAVMap(hashkey))

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (hashKeyName: String, hashKeyValue: String, rangeKeyName: String, rangeKeyValue: Long, tableName: String) =>
    *     |   val deleteRequest = ScanamoRequest.deleteRequest[String, Long](tableName)(Symbol(hashKeyName) -> hashKeyValue, Symbol(rangeKeyName) -> rangeKeyValue)
    *     |   deleteRequest.getTableName == tableName &&
    *     |   deleteRequest.getKey == Map(hashKeyName -> new AttributeValue().withS(hashKeyValue), rangeKeyName -> new AttributeValue().withN(rangeKeyValue.toString)).asJava
    * }}}
    */
  def deleteRequest[HK, RK](tableName: String)(hashkey: (Symbol, HK), rangekey: (Symbol, RK))
    (implicit fhk: DynamoFormat[HK], frk: DynamoFormat[RK]): DeleteItemRequest =
    new DeleteItemRequest().withTableName(tableName).withKey(mergeAVMaps(asAVMap(hashkey), asAVMap(rangekey)))

  def queryRequest[K](tableName: String)(key: (Symbol, K))(implicit fk: DynamoFormat[K]): QueryRequest = {
    val (k, v) = key
    new QueryRequest().withTableName(tableName)
      .withKeyConditionExpression(s"#K = :${k.name}")
      .withExpressionAttributeNames(Map("#K" -> k.name).asJava)
      .withExpressionAttributeValues(asAVMap(Symbol(s":${k.name}") -> v))
  }

  private def asAVMap[K](kvs: (Symbol, K)*)(implicit fk: DynamoFormat[K]) =
    Map(kvs: _*).map { case (k,v) => (k.name, fk.write(v)) }.asJava

  private def mergeAVMaps(maps: java.util.Map[String, AttributeValue]*) = {
    val map = new java.util.HashMap[String, AttributeValue]()
    maps.foreach(m => map.putAll(m))
    map
  }

}
