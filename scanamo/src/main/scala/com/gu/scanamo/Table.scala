package org.scanamo

import org.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import org.scanamo.error.DynamoReadError
import org.scanamo.ops.ScanamoOps
import org.scanamo.query._
import org.scanamo.request.{ScanamoQueryOptions, ScanamoQueryRequest, ScanamoScanRequest}
import org.scanamo.update.UpdateExpression
import software.amazon.awssdk.services.dynamodb.model.{BatchWriteItemResponse, DeleteItemResponse, QueryResponse, ScanResponse}

import scala.collection.JavaConverters._


case class Table[V: DynamoFormat](name: String) {

  def put(v: V): ScanamoOps[Option[Either[DynamoReadError, V]]] = ScanamoFree.put(name)(v)
  def putAll(vs: Set[V]): ScanamoOps[List[BatchWriteItemResponse]] = ScanamoFree.putAll(name)(vs)
  def get(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]] = ScanamoFree.get[V](name)(key)
  def getAll(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, V]]] = ScanamoFree.getAll[V](name)(keys)
  def delete(key: UniqueKey[_]): ScanamoOps[DeleteItemResponse] = ScanamoFree.delete(name)(key)

  def deleteAll(items: UniqueKeys[_]): ScanamoOps[List[BatchWriteItemResponse]] = ScanamoFree.deleteAll(name)(items)

  def index(indexName: String): SecondaryIndex[V] =
    SecondaryIndexWithOptions[V](name, indexName, ScanamoQueryOptions.default)

  def update(key: UniqueKey[_], expression: UpdateExpression): ScanamoOps[Either[DynamoReadError, V]] =
    ScanamoFree.update[V](name)(key)(expression)

  def limit(n: Int) = TableWithOptions[V](name, ScanamoQueryOptions.default).limit(n)


  def consistently = ConsistentlyReadTable(name)


  def given[T: ConditionExpression](condition: T) = ConditionalOperation[V, T](name, condition)


  def from[K: UniqueKeyCondition](key: UniqueKey[K]) = TableWithOptions(name, ScanamoQueryOptions.default).from(key)


  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] = ScanamoFree.scan[V](name)


  def scan0: ScanamoOps[ScanResponse] = ScanamoFree.scan0[V](name)

  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] = ScanamoFree.query[V](name)(query)

  def query0(query: Query[_]): ScanamoOps[QueryResponse] = ScanamoFree.query0[V](name)(query)

  def filter[C: ConditionExpression](condition: C) =
    TableWithOptions(name, ScanamoQueryOptions.default).filter(Condition(condition))
}

private[scanamo] case class ConsistentlyReadTable[V: DynamoFormat](tableName: String) {
  def limit(n: Int): TableWithOptions[V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.limit(n)
  def from[K: UniqueKeyCondition](key: UniqueKey[K]) =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.from(key)
  def filter[T](c: Condition[T]): TableWithOptions[V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.filter(c)
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.scan()
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.query(query)

  def get(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]] =
    ScanamoFree.getWithConsistency[V](tableName)(key)
  def getAll(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, V]]] =
    ScanamoFree.getAllWithConsistency[V](tableName)(keys)
}

private[scanamo] case class TableWithOptions[V: DynamoFormat](tableName: String, queryOptions: ScanamoQueryOptions) {
  def limit(n: Int): TableWithOptions[V] = copy(queryOptions = queryOptions.copy(limit = Some(n)))
  def consistently: TableWithOptions[V] = copy(queryOptions = queryOptions.copy(consistent = true))
  def from[K: UniqueKeyCondition](key: UniqueKey[K]) =
    copy(queryOptions = queryOptions.copy(exclusiveStartKey = Some(key.asAVMap.asJava)))
  def filter[T](c: Condition[T]): TableWithOptions[V] = copy(queryOptions = queryOptions.copy(filter = Some(c)))

  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] =
    ScanResultStream.stream[V](ScanamoScanRequest(tableName, None, queryOptions)).map(_._1)
  def scan0: ScanamoOps[ScanResponse] =
    ScanamoOps.scan(ScanamoScanRequest(tableName, None, queryOptions))
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] =
    QueryResultStream.stream[V](ScanamoQueryRequest(tableName, None, query, queryOptions)).map(_._1)
  def query0(query: Query[_]): ScanamoOps[QueryResponse] =
    ScanamoOps.query(ScanamoQueryRequest(tableName, None, query, queryOptions))
}
