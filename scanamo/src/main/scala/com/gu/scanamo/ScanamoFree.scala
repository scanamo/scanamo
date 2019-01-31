package org.scanamo

import org.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import org.scanamo.error.DynamoReadError
import org.scanamo.ops.ScanamoOps
import org.scanamo.query._
import org.scanamo.request._
import org.scanamo.update.UpdateExpression
import software.amazon.awssdk.services.dynamodb.model._

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
        if (Option(r.attributes()).exists(_.asScala.nonEmpty)) {
          Some(
            f.read {
              AttributeValue.builder().m(r.attributes()).build()
            }
          )
        } else {
          None
        }
      }

  def putAll[T](tableName: String)(items: Set[T])(implicit f: DynamoFormat[T]): ScanamoOps[List[BatchWriteItemResponse]] =
    items
      .grouped(batchSize)
      .toList
      .traverse(
        batch =>
          ScanamoOps.batchWrite(
            BatchWriteItemRequest.builder.requestItems(
              Map(
                tableName -> batch.toList
                  .map(i => WriteRequest.builder.putRequest(PutRequest.builder.item(f.write(i).m()).build()).build())
                  .asJava
              ).asJava
            ).build()
          )
      )

  def deleteAll(tableName: String)(items: UniqueKeys[_]): ScanamoOps[List[BatchWriteItemResponse]] =
    items.asAVMap.grouped(batchSize).toList.traverse { batch =>
      ScanamoOps.batchWrite(
        BatchWriteItemRequest.builder().requestItems(
          Map(
            tableName -> batch.toList
              .map(item => WriteRequest.builder().deleteRequest(DeleteRequest.builder().key(item.asJava).build()).build())
              .asJava
          ).asJava
        ).build()
      )
    }

  def get[T](
    tableName: String
  )(key: UniqueKey[_])(implicit ft: DynamoFormat[T]): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(GetItemRequest.builder.tableName(tableName).key(key.asAVMap.asJava).build())
    } yield Option(res.item()).map(read[T])

  def getWithConsistency[T](
    tableName: String
  )(key: UniqueKey[_])(implicit ft: DynamoFormat[T]): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    for {
      res <- ScanamoOps.get(
        GetItemRequest.builder.tableName(tableName).key(key.asAVMap.asJava).consistentRead(true).build()
      )
    } yield Option(res.item).map(read[T])

  def getAll[T: DynamoFormat](tableName: String)(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, T]]] =
    keys.asAVMap
      .grouped(batchSize)
      .toList
      .traverse { batch =>
        ScanamoOps.batchGet(
          BatchGetItemRequest.builder().requestItems(
            Map(
              tableName ->
                KeysAndAttributes.builder.keys(batch.map(_.asJava).asJava).build()
            ).asJava
          ).build()
        )
      }
      .map(_.flatMap(_.responses().get(tableName).asScala.toSet.map(read[T])).toSet)

  def getAllWithConsistency[T: DynamoFormat](
    tableName: String
  )(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, T]]] =
    keys.asAVMap
      .grouped(batchSize)
      .toList
      .traverse { batch =>
        ScanamoOps.batchGet(
          BatchGetItemRequest.builder().requestItems(
            Map(
              tableName ->
                KeysAndAttributes.builder().keys(batch.map(_.asJava).asJava).consistentRead(true).build()
            ).asJava
          ).build()
        )
      }
      .map(_.flatMap(_.responses().get(tableName).asScala.toSet.map(read[T])).toSet)

  def delete(tableName: String)(key: UniqueKey[_]): ScanamoOps[DeleteItemResponse] =
    ScanamoOps.delete(ScanamoDeleteRequest(tableName, key.asAVMap, None))

  def scan[T: DynamoFormat](tableName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default)).map(_._1)

  def scan0[T: DynamoFormat](tableName: String): ScanamoOps[ScanResponse] =
    ScanamoOps.scan(ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default))

  def query[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default)).map(_._1)

  def query0[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[QueryResponse] =
    ScanamoOps.query(ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default))

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
        r => format.read {
          AttributeValue.builder().m(r.attributes).build()
        }
      )

  def read[T](m: java.util.Map[String, AttributeValue])(implicit f: DynamoFormat[T]): Either[DynamoReadError, T] =
    f.read(AttributeValue.builder().m(m).build())
}
