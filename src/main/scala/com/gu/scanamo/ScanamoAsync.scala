package com.gu.scanamo

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, DeleteItemResult, PutItemResult}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{ScanamoInterpreters, ScanamoOps}
import com.gu.scanamo.query.{Query, UniqueKey, UniqueKeys}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides the same interface as [[com.gu.scanamo.Scanamo]], except that it requires an implicit
  * concurrent.ExecutionContext and returns a concurrent.Future
  *
  * Note that that com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient just uses an
  * java.util.concurrent.ExecutorService to make calls asynchronously
  */
object ScanamoAsync {
  import cats.std.future._

  def exec[A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A])(implicit ec: ExecutionContext) =
    op.foldMap(ScanamoInterpreters.future(client)(ec))

  def put[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(item: T)
    (implicit ec: ExecutionContext): Future[PutItemResult] =
    exec(client)(ScanamoFree.put(tableName)(item))

  def putAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(items: List[T])
    (implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  def get[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
    (implicit ec: ExecutionContext): Future[Option[Xor[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  def getAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])
    (implicit ec: ExecutionContext): Future[List[Xor[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  def delete[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
    (implicit ec: ExecutionContext): Future[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  def scan[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)
    (implicit ec: ExecutionContext): Future[Stream[Xor[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  def scanIndex[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)
    (implicit ec: ExecutionContext): Future[Stream[Xor[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  def query[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_])
    (implicit ec: ExecutionContext): Future[Stream[Xor[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  def queryIndex[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(query: Query[_])
    (implicit ec: ExecutionContext): Future[Stream[Xor[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))
}
