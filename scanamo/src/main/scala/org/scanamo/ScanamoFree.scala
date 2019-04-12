package org.scanamo

import com.amazonaws.services.dynamodbv2.model.{PutRequest, WriteRequest, _}
import org.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import org.scanamo.error.{DynamoDBException, DynamoReadError, ScanamoError}
import org.scanamo.ops.ScanamoOps
import org.scanamo.ops.ScanamoOpsA.ScanamoResult
import org.scanamo.query._
import org.scanamo.request._
import org.scanamo.update.UpdateExpression

object ScanamoFree {

  import cats.instances.list._
  import cats.syntax.traverse._
  import collection.JavaConverters._

  private val batchSize = 25

  def put[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): ScanamoOps[Option[Either[ScanamoError, T]]] =
    ScanamoOps
      .put(
        ScanamoPutRequest(tableName, f.write(item), None)
      )
      .map {
        _.fold(
          e => Some(Left(DynamoDBException(e))),
          r =>
            if (Option(r.getAttributes).exists(_.asScala.nonEmpty)) {
              Some(
                f.read(new AttributeValue().withM(r.getAttributes))
              )
            } else {
              None
            }
        )
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
  )(key: UniqueKey[_])(implicit ft: DynamoFormat[T]): ScanamoOps[Option[Either[ScanamoError, T]]] =
    for {
      res <- ScanamoOps.get(new GetItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava))
    } yield res.fold(
        e => Some(Left(DynamoDBException(e))),
        r =>
          Option(r.getItem).map(read[T])
      )

  def getWithConsistency[T](
    tableName: String
  )(key: UniqueKey[_])(implicit ft: DynamoFormat[T]): ScanamoOps[Option[Either[ScanamoError, T]]] =
    for {
      res <- ScanamoOps.get(
        new GetItemRequest().withTableName(tableName).withKey(key.asAVMap.asJava).withConsistentRead(true)
      )
    } yield res.fold(
        e => Some(Left(DynamoDBException(e))),
        r =>
          Option(r.getItem).map(read[T])
      )


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

  def delete(tableName: String)(key: UniqueKey[_]): ScanamoOps[ScanamoResult[DeleteItemResult]] =
    ScanamoOps.delete(ScanamoDeleteRequest(tableName, key.asAVMap, None))

  def scan[T: DynamoFormat](tableName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default)).map(_._1)

  def scan0[T: DynamoFormat](tableName: String): ScanamoOps[ScanResult] =
    ScanamoOps.scan(ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default))

  def query[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default)).map(_._1)

  def query0[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[QueryResult] =
    ScanamoOps.query(ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default))

  def update[T](tableName: String)(key: UniqueKey[_])(update: UpdateExpression)(
    implicit format: DynamoFormat[T]
  ): ScanamoOps[Either[ScanamoError, T]] =
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
      .map {
        _.fold(
          e => Left(DynamoDBException(e)),
          r => format.read(new AttributeValue().withM(r.getAttributes))
        )
      }

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
