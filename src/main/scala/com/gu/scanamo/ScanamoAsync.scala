package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, DeleteItemResult, PutItemResult, QueryRequest}
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

  def exec[A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A])(implicit ec: ExecutionContext) =
    op.foldMap(ScanamoInterpreters.future(client)(ec))

  def put[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(item: T)
    (implicit ec: ExecutionContext): Future[PutItemResult] =
    exec(client)(ScanamoFree.put(tableName)(item))

  def putAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(items: Set[T])
    (implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  def get[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
    (implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  def getWithConsistency[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
                          (implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  def getAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])
    (implicit ec: ExecutionContext): Future[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  def delete[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
    (implicit ec: ExecutionContext): Future[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  def deleteAll(client: AmazonDynamoDBAsync)(tableName: String)(items: UniqueKeys[_])
                             (implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  def update[V: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
    key: UniqueKey[_], expression: UpdateExpression)(implicit ec: ExecutionContext
  ): Future[Either[DynamoReadError,V]] =
    exec(client)(ScanamoFree.update[V](tableName)(key)(expression))

  def scan[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)
    (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  def scanWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, limit: Int)
    (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit))

  def scanIndex[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)
    (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  def scanIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String, limit: Int)
    (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit))

  def query[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_], queryRequest: QueryRequest = new QueryRequest())
    (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query, queryRequest))

  def queryWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_], limit: Int, queryRequest: QueryRequest = new QueryRequest())
    (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit, queryRequest))

  def queryIndex[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(query: Query[_], queryRequest: QueryRequest = new QueryRequest())
    (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query, queryRequest))

  def queryIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String, queryRequest: QueryRequest = new QueryRequest())(
    query: Query[_], limit: Int)(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit, queryRequest))
}
