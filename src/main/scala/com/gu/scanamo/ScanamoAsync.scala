package com.gu.scanamo

import cats.data.{Streaming, ValidatedNel}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._

import scala.concurrent.{ExecutionContext, Future}

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
    (implicit ec: ExecutionContext): Future[Option[ValidatedNel[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  def getAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])
    (implicit ec: ExecutionContext): Future[List[ValidatedNel[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  def delete[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
    (implicit ec: ExecutionContext): Future[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  def scan[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)
    (implicit ec: ExecutionContext): Future[Streaming[ValidatedNel[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  def query[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_])
    (implicit ec: ExecutionContext): Future[Streaming[ValidatedNel[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

}
