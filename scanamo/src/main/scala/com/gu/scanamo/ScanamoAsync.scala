package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, DeleteItemResult}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{ScanamoInterpreters, ScanamoOps}
import com.gu.scanamo.query.{Query, UniqueKey, UniqueKeys}
import com.gu.scanamo.update.UpdateExpression
import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides the same interface as [[com.gu.scanamo.Scanamo]], except that it requires an implicit
  * concurrent.ExecutionContext and returns a concurrent.Future
  *
  * Note that that com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient just uses an
  * java.util.concurrent.ExecutorService to make calls asynchronously
  */
object ScanamoAsync {
  import cats.instances.future._

  /**
    * Execute the operations built with [[com.gu.scanamo.Table]], using the client
    * provided asynchronously
    */
  def exec[A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A])(implicit ec: ExecutionContext) =
    op.foldMap(ScanamoInterpreters.future(client)(ec))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.put]]", "1.0")
  def put[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(item: T)(
      implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.put(tableName)(item))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.putAll]]", "1.0")
  def putAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(items: Set[T])(
      implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.get]]", "1.0")
  def get[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])(
      implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.consistently]]", "1.0")
  def getWithConsistency[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])(
      implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.getAll]]", "1.0")
  def getAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])(
      implicit ec: ExecutionContext): Future[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  def getAllWithConsistency[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])(
      implicit ec: ExecutionContext): Future[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAllWithConsistency[T](tableName)(keys))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.delete]]", "1.0")
  def delete[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])(
      implicit ec: ExecutionContext): Future[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.deleteAll]]", "1.0")
  def deleteAll(client: AmazonDynamoDBAsync)(tableName: String)(items: UniqueKeys[_])(
      implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.update]]", "1.0")
  def update[V: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      key: UniqueKey[_],
      expression: UpdateExpression)(implicit ec: ExecutionContext): Future[Either[DynamoReadError, V]] =
    exec(client)(ScanamoFree.update[V](tableName)(key)(expression))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.scan]]", "1.0")
  def scan[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.limit]]", "1.0")
  def scanWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, limit: Int)(
      implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit))

  def scanFrom[T: DynamoFormat](
      client: AmazonDynamoDBAsync)(tableName: String, limit: Int, startKey: Option[EvaluationKey])(
      implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanFrom(tableName, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]]", "1.0")
  def scanIndex[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(
      implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]] and [[com.gu.scanamo.SecondaryIndex.limit]]", "1.0")
  def scanIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String,
      indexName: String,
      limit: Int)(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit))

  def scanIndexFrom[T: DynamoFormat](
      client: AmazonDynamoDBAsync)(tableName: String, indexName: String, limit: Int, startKey: Option[EvaluationKey])(
      implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanIndexFrom(tableName, indexName, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.query]]", "1.0")
  def query[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_])(
      implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.limit]]", "1.0")
  def queryWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_], limit: Int)(
      implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit))

  def queryFrom[T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String)(query: Query[_], limit: Int, startKey: Option[EvaluationKey])(
      implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryFrom(tableName)(query, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]]", "1.0")
  def queryIndex[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(query: Query[_])(
      implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]] and [[com.gu.scanamo.SecondaryIndex.limit]]", "1.0")
  def queryIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(
      query: Query[_],
      limit: Int)(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit))

  def queryIndexFrom[T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String,
      indexName: String)(query: Query[_], limit: Int, startKey: Option[EvaluationKey])(
      implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryIndexFrom(tableName, indexName)(query, limit, startKey))
}
