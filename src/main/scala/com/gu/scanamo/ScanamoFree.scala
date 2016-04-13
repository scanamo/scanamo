package com.gu.scanamo

import cats.data.{Streaming, ValidatedNel}
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}

object ScanamoFree {
  import ScanamoRequest._
  import cats.std.list._
  import cats.syntax.traverse._

  def put[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): ScanamoOps[PutItemResult] =
    ScanamoOps.put(putRequest(tableName)(item))

  def putAll[T: DynamoFormat](tableName: String)(items: List[T]): ScanamoOps[List[BatchWriteItemResult]] =
    items.grouped(25).toList.traverseU(batch =>
      ScanamoOps.batchWrite(batchPutRequest(tableName)(batch)))

  def get[T](tableName: String)(key: UniqueKey[_])
    (implicit ft: DynamoFormat[T]): ScanamoOps[Option[ValidatedNel[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(getRequest(tableName)(key))
    } yield
      Option(res.getItem).map(read[T])

  def getAll[T: DynamoFormat](tableName: String)(keys: UniqueKeys[_]): ScanamoOps[List[ValidatedNel[DynamoReadError, T]]] = {
    import collection.convert.decorateAsScala._
    for {
      res <- ScanamoOps.batchGet(batchGetRequest(tableName)(keys))
    } yield
      keys.sortByKeys(res.getResponses.get(tableName).asScala.toList).map(read[T])
  }

  def delete(tableName: String)(key: UniqueKey[_]): ScanamoOps[DeleteItemResult] =
    ScanamoOps.delete(deleteRequest(tableName)(key))

  def scan[T: DynamoFormat](tableName: String): ScanamoOps[Streaming[ValidatedNel[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName))

  def query[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[Streaming[ValidatedNel[DynamoReadError, T]]] =
    QueryResultStream.stream[T](queryRequest(tableName)(query))

  def queryByIndex[T: DynamoFormat](tableName: String, indexName: String)(query: Query[_]): ScanamoOps[Streaming[ValidatedNel[DynamoReadError, T]]] =
    QueryResultStream.stream[T](queryRequest(tableName)(query).withIndexName(indexName))

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (m: Map[String, Int]) =>
    *     |   ScanamoFree.read[Map[String, Int]](
    *     |     m.mapValues(i => new AttributeValue().withN(i.toString)).asJava
    *     |   ) == cats.data.Validated.valid(m)
    * }}}
    */
  def read[T](m: java.util.Map[String, AttributeValue])(implicit f: DynamoFormat[T]): ValidatedNel[DynamoReadError, T] =
    f.read(new AttributeValue().withM(m))
}
