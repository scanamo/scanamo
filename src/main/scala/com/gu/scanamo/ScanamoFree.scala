package com.gu.scanamo

import cats.data.{Streaming, ValidatedNel}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}

object ScanamoFree {
  import ScanamoRequest._

  def put[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): ScanamoOps[PutItemResult] =
    ScanamoOps.put(putRequest(tableName)(item))

  def putAll[T](client: AmazonDynamoDB)(tableName: String)(items: List[T])(implicit f: DynamoFormat[T]): List[BatchWriteItemResult] =
    (for {
      batch <- items.grouped(25)
    } yield client.batchWriteItem(batchPutRequest(tableName)(batch))).toList


  def get[T](tableName: String)(key: UniqueKey[_])
    (implicit ft: DynamoFormat[T]): ScanamoOps[Option[ValidatedNel[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(getRequest(tableName)(key))
    } yield
      Option(res.getItem).map(read[T])

  def getAll[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(keys: UniqueKeys[_]): List[ValidatedNel[DynamoReadError, T]] = {
    import collection.convert.decorateAsScala._
    keys.sortByKeys(client.batchGetItem(batchGetRequest(tableName)(keys)).getResponses.get(tableName).asScala.toList)
      .map(read[T])
  }

  def delete(tableName: String)(key: UniqueKey[_]): ScanamoOps[DeleteItemResult] =
    ScanamoOps.delete(deleteRequest(tableName)(key))

  def scan[T: DynamoFormat](tableName: String): ScanamoOps[Streaming[ValidatedNel[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName))

  def query[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[Streaming[ValidatedNel[DynamoReadError, T]]] =
    QueryResultStream.stream[T](queryRequest(tableName)(query))

  def read[T](m: java.util.Map[String, AttributeValue])(implicit f: DynamoFormat[T]): ValidatedNel[DynamoReadError, T] =
    f.read(new AttributeValue().withM(m))
}
