/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo

import cats.{ Monad, MonoidK }
import com.amazonaws.services.dynamodbv2.model._
import java.util.{ List => JList, Map => JMap }
import org.scanamo.DynamoResultStream.{ QueryResultStream, ScanResultStream }
import org.scanamo.ops.{ ScanamoOps, ScanamoOpsT }
import org.scanamo.query._
import org.scanamo.request._
import org.scanamo.update.UpdateExpression

object ScanamoFree {
  import cats.instances.list._
  import cats.syntax.functor._
  import cats.syntax.applicative._
  import cats.syntax.traverse._
  import collection.JavaConverters._

  private val batchSize = 25
  private val batchGetSize = 100

  def put[T: DynamoFormat](tableName: String)(item: T): ScanamoOps[Unit] =
    nativePut(tableName, PutReturn.Nothing, item).void

  def putAndReturn[T: DynamoFormat](tableName: String)(ret: PutReturn,
                                                       item: T): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    nativePut(tableName, ret, item)
      .map(r => Option(r.getAttributes).filterNot(_.isEmpty).map(DynamoObject(_)).map(read[T]))

  private def nativePut[T](tableName: String, ret: PutReturn, item: T)(
    implicit f: DynamoFormat[T]
  ): ScanamoOps[PutItemResult] =
    ScanamoOps.put(ScanamoPutRequest(tableName, f.write(item), None, ret))

  def putAll[T](tableName: String)(items: Set[T])(implicit f: DynamoFormat[T]): ScanamoOps[Unit] = {
    def loop(items: List[JMap[String, JList[WriteRequest]]]): ScanamoOps[Unit] = items match {
      case Nil => ().pure[ScanamoOps]
      case map :: rest =>
        ScanamoOps.batchWrite(new BatchWriteItemRequest().withRequestItems(map)).flatMap { resp =>
          val unprocessed = resp.getUnprocessedItems
          loop(if (unprocessed.isEmpty) rest else unprocessed :: rest)
        }
    }

    val batches = items
      .grouped(batchSize)
      .map { batch =>
        buildMap[T, WriteRequest](
          tableName,
          batch,
          item =>
            new WriteRequest()
              .withPutRequest(new PutRequest().withItem(f.write(item).asObject.getOrElse(DynamoObject.empty).toJavaMap))
        )
      }
      .toList

    loop(batches)
  }

  def transactPutAllTable[T](
    tableName: String
  )(items: List[T])(implicit f: DynamoFormat[T]): ScanamoOps[TransactWriteItemsResult] =
    transactPutAll(items.map(tableName -> _))

  def transactPutAll[T](
    tableAndItems: List[(String, T)]
  )(implicit f: DynamoFormat[T]): ScanamoOps[TransactWriteItemsResult] = {
    val dItems = tableAndItems.map {
      case (tableName, itm) =>
        TransactPutItem(tableName, f.write(itm), None)
    }
    ScanamoOps.transactWriteAll(ScanamoTransactWriteRequest(dItems, Seq.empty, Seq.empty))
  }

  def transactUpdateAllTable(
    tableName: String
  )(items: List[(UniqueKey[_], UpdateExpression)]): ScanamoOps[TransactWriteItemsResult] =
    transactUpdateAll(items.map(tableName → _))

  def transactUpdateAll(
    tableAndItems: List[(String, (UniqueKey[_], UpdateExpression))]
  ): ScanamoOps[TransactWriteItemsResult] = {
    val items = tableAndItems.map {
      case (tableName, (key, updateExpression)) ⇒
        TransactUpdateItem(tableName, key.toDynamoObject, updateExpression, None)
    }
    ScanamoOps.transactWriteAll(ScanamoTransactWriteRequest(Seq.empty, items, Seq.empty))
  }

  def transactDeleteAllTable(
    tableName: String
  )(items: List[UniqueKey[_]]): ScanamoOps[TransactWriteItemsResult] =
    transactDeleteAll(items.map(tableName → _))

  def transactDeleteAll(
    tableAndItems: List[(String, UniqueKey[_])]
  ): ScanamoOps[TransactWriteItemsResult] = {
    val items = tableAndItems.map {
      case (tableName, key) ⇒
        TransactDeleteItem(tableName, key.toDynamoObject, None)
    }
    ScanamoOps.transactWriteAll(ScanamoTransactWriteRequest(Seq.empty, Seq.empty, items))
  }

  def deleteAll(tableName: String)(items: UniqueKeys[_]): ScanamoOps[Unit] =
    items.toDynamoObject
      .grouped(batchSize)
      .toList
      .traverse { batch =>
        val map = buildMap[DynamoObject, WriteRequest](
          tableName,
          batch,
          item => new WriteRequest().withDeleteRequest(new DeleteRequest().withKey(item.toJavaMap))
        )
        ScanamoOps.batchWrite(new BatchWriteItemRequest().withRequestItems(map))
      }
      // can't believe cats doesn't provide a version of traverse that doesn't accumulate the result
      .void

  def get[T: DynamoFormat](
    tableName: String
  )(key: UniqueKey[_], consistent: Boolean): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    ScanamoOps
      .get(
        new GetItemRequest()
          .withTableName(tableName)
          .withKey(key.toDynamoObject.toJavaMap)
          .withConsistentRead(consistent)
      )
      .map(res => Option(res.getItem).map(m => read[T](DynamoObject(m))))

  def getAll[T: DynamoFormat](
    tableName: String
  )(keys: UniqueKeys[_], consistent: Boolean): ScanamoOps[Set[Either[DynamoReadError, T]]] =
    keys.toDynamoObject
      .grouped(batchGetSize)
      .toList
      .traverse { batch =>
        val map = emptyMap[String, KeysAndAttributes](1)
        map.put(
          tableName,
          new KeysAndAttributes()
            .withKeys(
              batch.foldLeft(emptyList[JMap[String, AttributeValue]](batch.size)) {
                case (keys, key) =>
                  keys.add(key.toJavaMap)
                  keys
              }
            )
            .withConsistentRead(consistent)
        )
        ScanamoOps.batchGet(new BatchGetItemRequest().withRequestItems(map))
      }
      .map(_.flatMap(_.getResponses.get(tableName).asScala.map(m => read[T](DynamoObject(m)))))
      .map(_.toSet)

  def delete(tableName: String)(key: UniqueKey[_]): ScanamoOps[Unit] =
    nativeDelete(tableName, key, DeleteReturn.Nothing).void

  def deleteAndReturn[T: DynamoFormat](
    tableName: String
  )(ret: DeleteReturn, key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, T]]] =
    nativeDelete(tableName, key, ret)
      .map(r => Option(r.getAttributes).filterNot(_.isEmpty).map(DynamoObject(_)).map(read[T]))

  def nativeDelete(tableName: String, key: UniqueKey[_], ret: DeleteReturn): ScanamoOps[DeleteItemResult] =
    ScanamoOps.delete(ScanamoDeleteRequest(tableName, key.toDynamoObject, None, ret))

  def scan[T: DynamoFormat](tableName: String): ScanamoOps[List[Either[DynamoReadError, T]]] =
    ScanResultStream.stream[T](ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default)).map(_._1)

  def scanM[M[_]: Monad: MonoidK, T: DynamoFormat](tableName: String,
                                                   pageSize: Int): ScanamoOpsT[M, List[Either[DynamoReadError, T]]] =
    ScanResultStream.streamTo[M, T](ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default), pageSize)

  def scan0[T: DynamoFormat](tableName: String): ScanamoOps[ScanResult] =
    ScanamoOps.scan(ScanamoScanRequest(tableName, None, ScanamoQueryOptions.default))

  def query[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, T]]] =
    QueryResultStream.stream[T](ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default)).map(_._1)

  def queryM[M[_]: Monad: MonoidK, T: DynamoFormat](
    tableName: String
  )(query: Query[_], pageSize: Int): ScanamoOpsT[M, List[Either[DynamoReadError, T]]] =
    QueryResultStream.streamTo[M, T](ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default), pageSize)

  def query0[T: DynamoFormat](tableName: String)(query: Query[_]): ScanamoOps[QueryResult] =
    ScanamoOps.query(ScanamoQueryRequest(tableName, None, query, ScanamoQueryOptions.default))

  def update[T: DynamoFormat](
    tableName: String
  )(key: UniqueKey[_])(update: UpdateExpression): ScanamoOps[Either[DynamoReadError, T]] =
    ScanamoOps
      .update(
        ScanamoUpdateRequest(
          tableName,
          key.toDynamoObject,
          update.expression,
          update.attributeNames,
          DynamoObject(update.dynamoValues),
          update.addEmptyList,
          None
        )
      )
      .map(r => read[T](DynamoObject(r.getAttributes)))

  /**
    * {{{
    * prop> (m: Map[String, Int]) =>
    *     |   ScanamoFree.read[Map[String, Int]](
    *     |     DynamoObject(m.mapValues(DynamoValue.fromNumber(_)).toMap)
    *     |   ) == Right(m)
    * }}}
    */
  def read[T](m: DynamoObject)(implicit f: DynamoFormat[T]): Either[DynamoReadError, T] =
    f.read(m.toDynamoValue)

  private def emptyList[T](capacity: Int): JList[T] = new java.util.ArrayList[T](capacity)
  private def emptyMap[K, T](capacity: Int): JMap[K, T] = new java.util.HashMap[K, T](capacity, 1)

  private def buildMap[A, B](tableName: String, batch: Iterable[A], f: A => B): JMap[String, JList[B]] = {
    val map = emptyMap[String, JList[B]](1)
    map.put(
      tableName,
      batch
        .foldLeft(emptyList[B](batch.size)) {
          case (reqs, i) =>
            reqs.add(f(i))
            reqs
        }
    )
    map
  }
}
