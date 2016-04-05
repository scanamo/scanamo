package com.gu.scanamo

import cats.data.{Streaming, ValidatedNel}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBAsync}
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}

import scala.concurrent.{ExecutionContext, Future}

object ScanamoAsync {
  import cats.std.future._

  def put[T](client: AmazonDynamoDBAsync)(tableName: String)(item: T)(
    implicit f: DynamoFormat[T], ec: ExecutionContext): Future[PutItemResult] =
    ScanamoFree.put(tableName)(item).foldMap(ScanamoInterpreters.future(client)(ec))

  def get[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
    (implicit ft: DynamoFormat[T], ec: ExecutionContext): Future[Option[ValidatedNel[DynamoReadError, T]]] =
    ScanamoFree.get[T](tableName)(key).foldMap(ScanamoInterpreters.future(client)(ec))

}
