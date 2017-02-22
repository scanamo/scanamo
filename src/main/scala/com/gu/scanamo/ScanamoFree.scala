package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.{PutRequest, WriteRequest, _}
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query._
import com.gu.scanamo.request.{ScanamoDeleteRequest, ScanamoPutRequest, ScanamoUpdateRequest}
import com.gu.scanamo.update.UpdateExpression

object ScanamoFree {

  import cats.instances.list._
  import cats.syntax.traverse._
  import collection.JavaConverters._

  private val batchSize = 25

  def put[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): ScanamoOps[PutItemResult] =
    ScanamoOps.put(ScanamoPutRequest(tableName, f.write(item), None))

  def putAll[T](tableName: String)(items: Set[T])(implicit f: DynamoFormat[T]): ScanamoOps[List[BatchWriteItemResult]] =
    items.grouped(batchSize).toList.traverseU(batch =>
      ScanamoOps.batchWrite(
        new BatchWriteItemRequest().withRequestItems(Map(tableName -> batch.toList.map(i =>
          new WriteRequest().withPutRequest(new PutRequest().withItem(f.write(i).getM))
        ).asJava).asJava)
      )
    )

  def deleteAll(tableName: String)(items: UniqueKeys[_]): ScanamoOps[List[BatchWriteItemResult]] = {
    items.asAVMap.grouped(batchSize).toList.traverseU { batch =>
      ScanamoOps.batchWrite(
        new BatchWriteItemRequest().withRequestItems(
          Map(tableName -> batch.toList
            .map(item =>
              new WriteRequest().withDeleteRequest(
                new DeleteRequest().withKey(item.asJava)))
            .asJava).asJava)
      )
    }
  }

  def get[T](tableName: String)(key: UniqueKey[_])
    (implicit ft: DynamoFormat[T]): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(new GetItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava))
    } yield
      Option(res.getItem).map(read[T])

  def getWithConsistency[T](tableName: String)(key: UniqueKey[_])
            (implicit ft: DynamoFormat[T]): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(new GetItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava).withConsistentRead(true))
    } yield
      Option(res.getItem).map(read[T])


  def getAll[T: DynamoFormat](tableName: String)(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, T]]] = {
    for {
      res <- ScanamoOps.batchGet(
        new BatchGetItemRequest().withRequestItems(Map(tableName ->
          new KeysAndAttributes().withKeys(keys.asAVMap.map(_.asJava).asJava)
        ).asJava)
      )
    } yield
      res.getResponses.get(tableName).asScala.toSet.map(read[T])
  }

  def delete(tableName: String)(key: UniqueKey[_]): ScanamoOps[DeleteItemResult] =
    ScanamoOps.delete(ScanamoDeleteRequest(tableName, key.asAVMap, None))

  def scan[T: DynamoFormat](tableName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName))

  def scanConsistent[T: DynamoFormat](tableName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName).withConsistentRead(true))

  def scanWithLimit[T: DynamoFormat](tableName: String, limit: Int): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName).withLimit(limit))

  def scanIndex[T: DynamoFormat](tableName: String, indexName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName).withIndexName(indexName))

  def scanIndexWithLimit[T: DynamoFormat](tableName: String, indexName: String, limit: Int): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](new ScanRequest().withTableName(tableName).withIndexName(indexName).withLimit(limit))

  def query[T: DynamoFormat](tableName: String)(query: Query[_], queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](query(queryRequest.withTableName(tableName)))

  def queryConsistent[T: DynamoFormat](tableName: String)(query: Query[_], queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](query(queryRequest.withTableName(tableName).withConsistentRead(true)))

  def queryWithLimit[T: DynamoFormat](tableName: String)(query: Query[_], limit: Int, queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](query(queryRequest.withTableName(tableName)).withLimit(limit))

  def queryIndex[T: DynamoFormat](tableName: String, indexName: String)(query: Query[_], queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](query(queryRequest.withTableName(tableName)).withIndexName(indexName))

  def queryIndexWithLimit[T: DynamoFormat](tableName: String, indexName: String)(query: Query[_], limit: Int, queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](query(queryRequest.withTableName(tableName)).withIndexName(indexName).withLimit(limit))

  def update[T](tableName: String)(key: UniqueKey[_])(update: UpdateExpression)(
    implicit format: DynamoFormat[T]
  ): ScanamoOps[Either[DynamoReadError, T]] =
    ScanamoOps.update(ScanamoUpdateRequest(
      tableName, key.asAVMap, update.expression, update.attributeNames, update.attributeValues, None)
    ).map(
      r => format.read(new AttributeValue().withM(r.getAttributes))
    )

  /**
    * {{{
    * prop> import collection.JavaConverters._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (m: Map[String, Int]) =>
    *     |   ScanamoFree.read[Map[String, Int]](
    *     |     m.mapValues(i => new AttributeValue().withN(i.toString)).asJava
    *     |   ) == Right(m)
    * }}}
    */
  def read[T](m: java.util.Map[String, AttributeValue])(implicit f: DynamoFormat[T]): Either[DynamoReadError, T] =
    f.read(new AttributeValue().withM(m))
}
