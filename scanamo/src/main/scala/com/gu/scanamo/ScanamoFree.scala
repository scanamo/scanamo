package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.{PutRequest, WriteRequest, _}
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query._
import com.gu.scanamo.request._
import com.gu.scanamo.update.UpdateExpression

object ScanamoFree {

  import cats.instances.list._
  import cats.syntax.traverse._
  import collection.JavaConverters._

  private val batchSize = 25

  def put[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    ScanamoOps
      .put(
        ScanamoPutRequest(tableName, f.write(item), None)
      )
      .map { r =>
        if (Option(r.getAttributes).exists(_.asScala.nonEmpty)) {
          Some(
            f.read(new AttributeValue().withM(r.getAttributes))
          )
        } else {
          None
        }
      }

  def putAll[T](tableName: String)(items: Set[T])(implicit f: DynamoFormat[T]): ScanamoOps[List[BatchWriteItemResult]] =
    items
      .grouped(batchSize)
      .toList
      .traverse(
        batch =>
          ScanamoOps.batchWrite(
            new BatchWriteItemRequest().withRequestItems(
              Map(
                tableName -> batch.toList
                  .map(i => new WriteRequest().withPutRequest(new PutRequest().withItem(f.write(i).getM)))
                  .asJava
              ).asJava
            )
          )
      )

  def deleteAll(tableName: String)(items: UniqueKeys[_]): ScanamoOps[List[BatchWriteItemResult]] =
    items.asAVMap.grouped(batchSize).toList.traverse { batch =>
      ScanamoOps.batchWrite(
        new BatchWriteItemRequest().withRequestItems(
          Map(
            tableName -> batch.toList
              .map(item => new WriteRequest().withDeleteRequest(new DeleteRequest().withKey(item.asJava)))
              .asJava
          ).asJava
        )
      )
    }

  def get[T](
    tableName: String
  )(key: UniqueKey[_])(implicit ft: DynamoFormat[T]): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(new GetItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava))
    } yield Option(res.getItem).map(read[T])

  def getWithConsistency[T](
    tableName: String
  )(key: UniqueKey[_])(implicit ft: DynamoFormat[T]): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(
        new GetItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava).withConsistentRead(true)
      )
    } yield Option(res.getItem).map(read[T])

  def getAll[T: DynamoFormat](tableName: String)(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, T]]] =
    keys.asAVMap
      .grouped(batchSize)
      .toList
      .traverse { batch =>
        ScanamoOps.batchGet(
          new BatchGetItemRequest().withRequestItems(
            Map(
              tableName ->
                new KeysAndAttributes().withKeys(batch.map(_.asJava).asJava)
            ).asJava
          )
        )
      }
      .map(_.flatMap(_.getResponses.get(tableName).asScala.toSet.map(read[T])).toSet)

  def getAllWithConsistency[T: DynamoFormat](
    tableName: String
  )(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, T]]] =
    keys.asAVMap
      .grouped(batchSize)
      .toList
      .traverse { batch =>
        ScanamoOps.batchGet(
          new BatchGetItemRequest().withRequestItems(
            Map(
              tableName ->
                new KeysAndAttributes().withKeys(batch.map(_.asJava).asJava).withConsistentRead(true)
            ).asJava
          )
        )
      }
      .map(_.flatMap(_.getResponses.get(tableName).asScala.toSet.map(read[T])).toSet)

  def delete(tableName: String)(key: UniqueKey[_]): ScanamoOps[DeleteItemResult] =
    ScanamoOps.delete(ScanamoDeleteRequest(tableName, key.asAVMap, None))

  def scan[T: DynamoFormat](tableName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default)).map(_._1)

  def scan0[T: DynamoFormat](tableName: String): ScanamoOps[ScanResult] =
    ScanamoOps.scan(ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default))

  def scanConsistent[T: DynamoFormat](tableName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream
      .stream[T](ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default.copy(consistent = true)))
      .map(_._1)

  def scanWithLimit[T: DynamoFormat](tableName: String, limit: Int): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream
      .stream[T](ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default.copy(limit = Some(limit))))
      .map(_._1)

  def scanIndex[T: DynamoFormat](tableName: String, indexName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](ScanamoScanRequest(tableName, Some(indexName), ScanamoQueryOptions.default)).map(_._1)

  def scanIndexWithLimit[T: DynamoFormat](
    tableName: String,
    indexName: String,
    limit: Int
  ): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream
      .stream[T](ScanamoScanRequest(tableName, Some(indexName), ScanamoQueryOptions.default.copy(limit = Some(limit))))
      .map(_._1)

  def query[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default)).map(_._1)

  def query0[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[QueryResult] =
    ScanamoOps.query(ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default))

  def queryConsistent[T: DynamoFormat](
    tableName: String
  )(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream
      .stream[T](ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default.copy(consistent = true)))
      .map(_._1)

  def queryWithLimit[T: DynamoFormat](
    tableName: String
  )(query: Query[_], limit: Int): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream
      .stream[T](ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default.copy(limit = Some(limit))))
      .map(_._1)

  def queryIndex[T: DynamoFormat](tableName: String, indexName: String)(
    query: Query[_]
  ): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream
      .stream[T](ScanamoQueryRequest(tableName, Some(indexName), query, ScanamoQueryOptions.default))
      .map(_._1)

  def queryIndexWithLimit[T: DynamoFormat](
    tableName: String,
    indexName: String
  )(query: Query[_], limit: Int): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream
      .stream[T](
        ScanamoQueryRequest(tableName, Some(indexName), query, ScanamoQueryOptions.default.copy(limit = Some(limit)))
      )
      .map(_._1)

  def update[T](tableName: String)(key: UniqueKey[_])(update: UpdateExpression)(
    implicit format: DynamoFormat[T]
  ): ScanamoOps[Either[DynamoReadError, T]] =
    ScanamoOps
      .update(
        ScanamoUpdateRequest(
          tableName,
          key.asAVMap,
          update.expression,
          update.attributeNames,
          update.attributeValues,
          None
        )
      )
      .map(
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
