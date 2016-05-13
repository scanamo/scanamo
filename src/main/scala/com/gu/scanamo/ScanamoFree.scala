package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, BatchWriteItemResult, DeleteItemResult, PutItemResult, ScanRequest}
import cats.data.Xor
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query._

object ScanamoFree {

  import Requests._
  import cats.std.list._
  import cats.syntax.traverse._

  def given[T: ConditionExpression](tableName: String)(condition: T): ConditionalOperation[T] =
    ConditionalOperation(tableName, condition)

  def put[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): ScanamoOps[PutItemResult] =
    ScanamoOps.put(putRequest(tableName)(item))

  def putAll[T: DynamoFormat](tableName: String)(items: List[T]): ScanamoOps[List[BatchWriteItemResult]] =
    items.grouped(25).toList.traverseU(batch =>
      ScanamoOps.batchWrite(batchPutRequest(tableName)(batch)))

  def get[T](tableName: String)(key: UniqueKey[_])
    (implicit ft: DynamoFormat[T]): ScanamoOps[Option[Xor[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(getRequest(tableName)(key))
    } yield
      Option(res.getItem).map(read[T])

  def getAll[T: DynamoFormat](tableName: String)(keys: UniqueKeys[_]): ScanamoOps[List[Xor[DynamoReadError, T]]] = {
    import collection.convert.decorateAsScala._
    for {
      res <- ScanamoOps.batchGet(batchGetRequest(tableName)(keys))
    } yield
      keys.sortByKeys(res.getResponses.get(tableName).asScala.toList).map(read[T])
  }

  def delete(tableName: String)(key: UniqueKey[_]): ScanamoOps[DeleteItemResult] =
    ScanamoOps.delete(deleteRequest(tableName)(key))


  def scan[T: DynamoFormat](tableName: String): ScanamoOps[Stream[Xor[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName))

  def scanWithLimit[T: DynamoFormat](tableName: String, limit: Int): ScanamoOps[Stream[Xor[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName).withLimit(limit))

  def scanIndex[T: DynamoFormat](tableName: String, indexName: String): ScanamoOps[Stream[Xor[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName).withIndexName(indexName))

  def scanIndexWithLimit[T: DynamoFormat](tableName: String, indexName: String, limit: Int): ScanamoOps[Stream[Xor[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName).withIndexName(indexName).withLimit(limit))

  def query[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[Stream[Xor[DynamoReadError, T]]] =
    QueryResultStream.stream[T](queryRequest(tableName)(query))

  def queryWithLimit[T: DynamoFormat](tableName: String)(query: Query[_], limit: Int): ScanamoOps[Stream[Xor[DynamoReadError, T]]] =
    QueryResultStream.stream[T](queryRequest(tableName)(query).withLimit(limit))

  def queryIndex[T: DynamoFormat](tableName: String, indexName: String)(query: Query[_]): ScanamoOps[Stream[Xor[DynamoReadError, T]]] =
    QueryResultStream.stream[T](queryRequest(tableName)(query).withIndexName(indexName))

  def queryIndexWithLimit[T: DynamoFormat](tableName: String, indexName: String)(query: Query[_], limit: Int): ScanamoOps[Stream[Xor[DynamoReadError, T]]] = {
    println(queryRequest(tableName)(query).withIndexName(indexName).withLimit(limit))
    QueryResultStream.stream[T](queryRequest(tableName)(query).withIndexName(indexName).withLimit(limit))
  }

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (m: Map[String, Int]) =>
    *     |   ScanamoFree.read[Map[String, Int]](
    *     |     m.mapValues(i => new AttributeValue().withN(i.toString)).asJava
    *     |   ) == cats.data.Xor.right(m)
    * }}}
    */
  def read[T](m: java.util.Map[String, AttributeValue])(implicit f: DynamoFormat[T]): Xor[DynamoReadError, T] =
    f.read(new AttributeValue().withM(m))
}
