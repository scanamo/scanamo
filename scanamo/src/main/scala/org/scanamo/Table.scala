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

import cats.{Monad, MonoidK}
import software.amazon.awssdk.services.dynamodb.model.{QueryResponse, ScanResponse, TransactWriteItemsResponse, TransactionCanceledException}
import org.scanamo.DynamoResultStream.{QueryResponseStream, ScanResponseStream}
import org.scanamo.ops.{ScanamoOps, ScanamoOpsT}
import org.scanamo.query.*
import org.scanamo.request.{ScanamoQueryOptions, ScanamoQueryRequest, ScanamoScanRequest}
import org.scanamo.update.UpdateExpression

/** Represents a DynamoDB table that operations can be performed against
  */
case class Table[V: DynamoFormat](name: String) {

  def put(v: V): ScanamoOps[Unit] = ScanamoFree.put(name)(v)

  def putAndReturn(ret: PutReturn)(v: V): ScanamoOps[Option[Either[DynamoReadError, V]]] =
    ScanamoFree.putAndReturn(name)(ret, v)

  def putAll(vs: Set[V]): ScanamoOps[Unit] = ScanamoFree.putAll(name)(vs)

  def get(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]] = ScanamoFree.get[V](name)(key, false)

  def getAll(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, V]]] =
    ScanamoFree.getAll[V](name)(keys, false)

  def delete(key: UniqueKey[_]): ScanamoOps[Unit] = ScanamoFree.delete(name)(key)

  def deleteAndReturn(ret: DeleteReturn)(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]] =
    ScanamoFree.deleteAndReturn(name)(ret, key)

  /** Deletes multiple items by a unique key
    */
  def deleteAll(items: UniqueKeys[_]): ScanamoOps[Unit] = ScanamoFree.deleteAll(name)(items)

  /** A secondary index on the table which can be scanned, or queried against
    */
  def index(indexName: String): SecondaryIndex[V] =
    SecondaryIndexWithOptions[V](name, indexName, ScanamoQueryOptions.default)

  /** Updates an attribute that is not part of the key and returns the updated row
    */
  def update(key: UniqueKey[_], expression: UpdateExpression): ScanamoOps[Either[DynamoReadError, V]] =
    ScanamoFree.update[V](name)(key)(expression)

  /** Query or scan a table, limiting the number of items evaluated by Dynamo
    */
  def limit(n: Int) = TableWithOptions[V](name, ScanamoQueryOptions.default).limit(n)

  /** Perform strongly consistent
    * (http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html) read operations
    * against this table. Note that there is no equivalent on table indexes as consistent reads from secondary indexes
    * are not supported by DynamoDB
    */
  def consistently = ConsistentlyReadTable(name)

  /** Performs the chained operation, `put` if the condition is met
    */
  def when[T: ConditionExpression](condition: T) = ConditionalOperation[V, T](name, condition)

  /** Primes a search request with a key to start from:
    */
  def from[K: UniqueKeyCondition](key: UniqueKey[K]) = TableWithOptions(name, ScanamoQueryOptions.default).from(key)

  /** Primes a search request with a key to start from:
    * @param exclusiveStartKey
    *   A [[DynamoObject]] containing attributes that match the partition key and sort key of the table
    * @return
    *   A new [[Table]] which when queried will return items after the provided exclusive start key
    * @example
    *   {{{
    *   import org.scanamo._
    *   import org.scanamo.syntax._
    *
    *   val table: Table[_] = ???
    *   val exclusiveStartKey = DynamoObject(
    *     Map(
    *       "myPartitionKeyName" -> myPartitionKeyValue.asDynamoValue,
    *       "mySortKeyName" -> mySortKeyValue.asDynamoValue
    *     )
    *   )
    *   val tableStartingFromExclusiveStartKey: Table[_] = table.from(exclusiveStartKey)
    *   }}}
    */
  def from(exclusiveStartKey: DynamoObject) =
    TableWithOptions(name, ScanamoQueryOptions.default).from(exclusiveStartKey)

  /** Scans all elements of a table
    */
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] = ScanamoFree.scan[V](name)

  /** Performs a scan with the ability to introduce effects into the computation. This is useful for huge tables when
    * you don't want to load the whole of it in memory, but scan it page by page.
    *
    * To control how many maximum items to load at once, use [[scanPaginatedM]]
    */
  final def scanM[M[_]: Monad: MonoidK]: ScanamoOpsT[M, List[Either[DynamoReadError, V]]] = scanPaginatedM(Int.MaxValue)

  /** Performs a scan with the ability to introduce effects into the computation. This is useful for huge tables when
    * you don't want to load the whole of it in memory, but scan it page by page, with a maximum of `pageSize` items per
    * page..
    *
    * @note
    *   DynamoDB will only ever return maximum 1MB of data per scan, so `pageSize` is an upper bound.
    */
  def scanPaginatedM[M[_]: Monad: MonoidK](pageSize: Int): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    ScanamoFree.scanM[M, V](name, pageSize)

  @deprecated("use `scanRaw`", "1.0")
  def scan0: ScanamoOps[ScanResponse] = scanRaw

  /** Scans the table and returns the raw DynamoDB result. Sometimes, one might want to access metadata returned in the
    * `ScanResponse` object, such as the last evaluated key for example. `Table#scan` only returns a list of results, so
    * there is no place for putting that information: this is where `scan0` comes in handy!
    *
    * A particular use case is when one wants to paginate through result sets, say:
    */
  def scanRaw: ScanamoOps[ScanResponse] = ScanamoFree.scanRaw[V](name)

  /** Query a table based on the hash key and optionally the range key
    */
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] = ScanamoFree.query[V](name)(query)

  /** Performs a query with the ability to introduce effects into the computation. This is useful for huge tables when
    * you don't want to load the whole of it in memory, but scan it page by page.
    *
    * To control how many maximum items to load at once, use [[queryPaginatedM]]
    */
  final def queryM[M[_]: Monad: MonoidK](query: Query[_]): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    queryPaginatedM(query, Int.MaxValue)

  /** Performs a scan with the ability to introduce effects into the computation. This is useful for huge tables when
    * you don't want to load the whole of it in memory, but scan it page by page, with a maximum of `pageSize` items per
    * page.
    *
    * @note
    *   DynamoDB will only ever return maximum 1MB of data per query, so `pageSize` is an upper bound.
    */
  def queryPaginatedM[M[_]: Monad: MonoidK](query: Query[_],
                                            pageSize: Int
  ): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    ScanamoFree.queryM[M, V](name)(query, pageSize)

  @deprecated("use `queryRaw`", "1.0")
  def query0(query: Query[_]): ScanamoOps[QueryResponse] = queryRaw(query)

  /** Queries the table and returns the raw DynamoDB result. Sometimes, one might want to access metadata returned in
    * the `QueryResponse` object, such as the last evaluated key for example. `Table#query` only returns a list of
    * results, so there is no place for putting that information: this is where `query0` comes in handy!
    */
  def queryRaw(query: Query[_]): ScanamoOps[QueryResponse] = ScanamoFree.queryRaw[V](name)(query)

  /** Filter the results of a Scan or Query
    */
  def filter[C: ConditionExpression](condition: C) =
    TableWithOptions(name, ScanamoQueryOptions.default).filter(Condition(condition))

  def descending =
    TableWithOptions(name, ScanamoQueryOptions.default).descending

  def transactPutAll(vs: List[V]): ScanamoOps[Either[TransactionCanceledException, TransactWriteItemsResponse]] =
    ScanamoFree.transactPutAllTable(name)(vs)

  def transactUpdateAll(
    vs: List[(UniqueKey[_], UpdateExpression)]
  ): ScanamoOps[Either[TransactionCanceledException, TransactWriteItemsResponse]] =
    ScanamoFree.transactUpdateAllTable(name)(vs)

  def transactDeleteAll(vs: List[UniqueKey[_]]): ScanamoOps[Either[TransactionCanceledException, TransactWriteItemsResponse]] =
    ScanamoFree.transactDeleteAllTable(name)(vs)
}

private[scanamo] case class ConsistentlyReadTable[V: DynamoFormat](tableName: String) {
  def limit(n: Int): TableWithOptions[V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.limit(n)
  def descending: TableWithOptions[V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.descending
  def from[K: UniqueKeyCondition](key: UniqueKey[K]) =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.from(key)
  def from(exclusiveStartKey: DynamoObject) =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.from(exclusiveStartKey)
  def filter[T](c: Condition[T]): TableWithOptions[V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.filter(c)
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.scan()
  def scanM[M[_]: Monad: MonoidK]: ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    scanPaginatedM(Int.MaxValue)
  def scanPaginatedM[M[_]: Monad: MonoidK](pageSize: Int): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.scanPaginatedM[M](pageSize)
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.query(query)
  def queryM[M[_]: Monad: MonoidK](query: Query[_]): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    queryPaginatedM(query, Int.MaxValue)
  def queryPaginatedM[M[_]: Monad: MonoidK](query: Query[_],
                                            pageSize: Int
  ): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.queryPaginatedM(query, pageSize)

  def get(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]] =
    ScanamoFree.get[V](tableName)(key, true)
  def getAll(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, V]]] =
    ScanamoFree.getAll[V](tableName)(keys, true)
}

private[scanamo] case class TableWithOptions[V: DynamoFormat](tableName: String, queryOptions: ScanamoQueryOptions) {
  def limit(n: Int): TableWithOptions[V] = copy(queryOptions = queryOptions.copy(limit = Some(n)))
  def consistently: TableWithOptions[V] = copy(queryOptions = queryOptions.copy(consistent = true))
  def descending: TableWithOptions[V] = copy(queryOptions = queryOptions.copy(ascending = false))
  def from[K: UniqueKeyCondition](key: UniqueKey[K]) =
    copy(queryOptions = queryOptions.copy(exclusiveStartKey = Some(key.toDynamoObject)))
  def from(exclusiveStartKey: DynamoObject) =
    copy(queryOptions = queryOptions.copy(exclusiveStartKey = Some(exclusiveStartKey)))
  def filter[T](c: Condition[T]): TableWithOptions[V] =
    copy(queryOptions = queryOptions.copy(filter = Some(c)))

  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] =
    ScanResponseStream.stream[V](ScanamoScanRequest(tableName, None, queryOptions)).map(_._1)
  def scanM[M[_]: Monad: MonoidK]: ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    scanPaginatedM(Int.MaxValue)
  def scanPaginatedM[M[_]: Monad: MonoidK](pageSize: Int): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    ScanResponseStream.streamTo[M, V](ScanamoScanRequest(tableName, None, queryOptions), pageSize)
  def scanRaw: ScanamoOps[ScanResponse] =
    ScanamoOps.scan(ScanamoScanRequest(tableName, None, queryOptions))
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] =
    QueryResponseStream.stream[V](ScanamoQueryRequest(tableName, None, query, queryOptions)).map(_._1)
  def queryM[M[_]: Monad: MonoidK](query: Query[_]): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    queryPaginatedM(query, Int.MaxValue)
  def queryPaginatedM[M[_]: Monad: MonoidK](query: Query[_],
                                            pageSize: Int
  ): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    QueryResponseStream.streamTo[M, V](ScanamoQueryRequest(tableName, None, query, queryOptions), pageSize)
  def queryRaw(query: Query[_]): ScanamoOps[QueryResponse] =
    ScanamoOps.query(ScanamoQueryRequest(tableName, None, query, queryOptions))
}
