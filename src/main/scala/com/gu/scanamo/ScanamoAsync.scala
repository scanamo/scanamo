package com.gu.scanamo

import cats.data.{Streaming, ValidatedNel}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBAsync}
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}

import scala.concurrent.{ExecutionContext, Future}

object ScanamoAsync {
  import cats.std.future._

  def exec[A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A])(implicit ec: ExecutionContext) =
    op.foldMap(ScanamoInterpreters.future(client)(ec))

  def put[T](client: AmazonDynamoDBAsync)(tableName: String)(item: T)(
    implicit f: DynamoFormat[T], ec: ExecutionContext): Future[PutItemResult] =
    exec(client)(ScanamoFree.put(tableName)(item))

  def get[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
    (implicit ft: DynamoFormat[T], ec: ExecutionContext): Future[Option[ValidatedNel[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  def delete[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
    (implicit ec: ExecutionContext): Future[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

}
