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
import org.scanamo.DynamoResultStream.{QueryResponseStream, ScanResponseStream}
import org.scanamo.ops.{ScanamoOps, ScanamoOpsT}
import org.scanamo.query.{Condition, ConditionExpression, IndexKey, Query}
import org.scanamo.request.{ScanamoQueryOptions, ScanamoQueryRequest, ScanamoScanRequest}
import software.amazon.awssdk.services.dynamodb.model.{QueryResponse, ScanResponse}

/** Represents a secondary index on a DynamoDB table.
  *
  * Can be constructed via the [[org.scanamo.Table#index index]] method on [[org.scanamo.Table Table]]
  */
sealed abstract class SecondaryIndex[V] {

  /** Scan a secondary index
    */
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]]

  /** Performs a scan with the ability to introduce effects into the computation. This is
    * useful for huge tables when you don't want to load the whole of it in memory, but
    * scan it page by page.
    *
    * To control how many maximum items to load at once, use [[scanPaginatedM]]
    */
  final def scanM[M[_]: Monad: MonoidK]: ScanamoOpsT[M, List[Either[DynamoReadError, V]]] = scanPaginatedM(Int.MaxValue)

  /** Performs a scan with the ability to introduce effects into the computation. This is
    * useful for huge tables when you don't want to load the whole of it in memory, but
    * scan it page by page, with a maximum of `pageSize` items per page..
    *
    * @note DynamoDB will only ever return maximum 1MB of data per scan, so `pageSize` is an
    * upper bound.
    */
  def scanPaginatedM[M[_]: Monad: MonoidK](pageSize: Int): ScanamoOpsT[M, List[Either[DynamoReadError, V]]]

  /** Scans the table and returns the raw DynamoDB result.
    */
  def scan0: ScanamoOps[ScanResponse]

  /** Run a query against keys in a secondary index
    */
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]]

  /** Queries the table and returns the raw DynamoDB result.
    */
  def query0(query: Query[_]): ScanamoOps[QueryResponse]

  /** Performs a query with the ability to introduce effects into the computation. This is
    * useful for huge tables when you don't want to load the whole of it in memory, but
    * scan it page by page.
    *
    * To control how many maximum items to load at once, use [[queryPaginatedM]]
    */
  final def queryM[M[_]: Monad: MonoidK](query: Query[_]): ScanamoOpsT[M, List[Either[DynamoReadError, V]]] =
    queryPaginatedM(query, Int.MaxValue)

  /** Performs a scan with the ability to introduce effects into the computation. This is
    * useful for huge tables when you don't want to load the whole of it in memory, but
    * scan it page by page, with a maximum of `pageSize` items per page.
    *
    * @note DynamoDB will only ever return maximum 1MB of data per query, so `pageSize` is an
    * upper bound.
    */
  def queryPaginatedM[M[_]: Monad: MonoidK](query: Query[_],
                                            pageSize: Int
  ): ScanamoOpsT[M, List[Either[DynamoReadError, V]]]

  /** Query or scan an index, limiting the number of items evaluated by Dynamo
    */
  def limit(n: Int): SecondaryIndex[V]

  /** Filter the results of `scan` or `query` within DynamoDB
    *
    * Note that rows filtered out still count towards your consumed capacity
    */
  def filter[C: ConditionExpression](condition: C): SecondaryIndex[V]

  def descending: SecondaryIndex[V]

  def from[T, K](key: T)(implicit K: IndexKey.Aux[T, K]): SecondaryIndex[V]
}

private[scanamo] case class SecondaryIndexWithOptions[V: DynamoFormat](
  tableName: String,
  indexName: String,
  queryOptions: ScanamoQueryOptions
) extends SecondaryIndex[V] {
  def limit(n: Int): SecondaryIndexWithOptions[V] = copy(queryOptions = queryOptions.copy(limit = Some(n)))
  def from[T, K](key: T)(implicit K: IndexKey.Aux[T, K]) =
    copy(queryOptions = queryOptions.copy(exclusiveStartKey = Some(IndexKey.toDynamoObject(key))))
  def filter[C: ConditionExpression](condition: C) =
    SecondaryIndexWithOptions[V](tableName, indexName, ScanamoQueryOptions.default).filter(Condition(condition))
  def filter[T](c: Condition[T]): SecondaryIndexWithOptions[V] =
    copy(queryOptions = queryOptions.copy(filter = Some(c)))
  def descending: SecondaryIndexWithOptions[V] =
    copy(queryOptions = queryOptions.copy(ascending = false))
  def scan() = ScanResponseStream.stream[V](ScanamoScanRequest(tableName, Some(indexName), queryOptions)).map(_._1)
  def scanPaginatedM[M[_]: Monad: MonoidK](pageSize: Int) =
    ScanResponseStream.streamTo[M, V](ScanamoScanRequest(tableName, Some(indexName), queryOptions), pageSize)
  def scan0: ScanamoOps[ScanResponse] =
    ScanamoOps.scan(ScanamoScanRequest(tableName, Some(indexName), queryOptions))
  def query(query: Query[_]) =
    QueryResponseStream.stream[V](ScanamoQueryRequest(tableName, Some(indexName), query, queryOptions)).map(_._1)
  def query0(query: Query[_]): ScanamoOps[QueryResponse] =
    ScanamoOps.query(ScanamoQueryRequest(tableName, Some(indexName), query, queryOptions))
  def queryPaginatedM[M[_]: Monad: MonoidK](query: Query[_], pageSize: Int) =
    QueryResponseStream.streamTo[M, V](ScanamoQueryRequest(tableName, Some(indexName), query, queryOptions), pageSize)
}
