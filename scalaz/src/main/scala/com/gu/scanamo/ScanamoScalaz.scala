package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{ BatchWriteItemResult, DeleteItemResult }
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{ ScalazInterpreter, ScanamoOps }
import com.gu.scanamo.query._
import com.gu.scanamo.update.UpdateExpression
import scalaz.ioeffect.Task
import shims._

object ScanamoScalaz {

  def exec[A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A]): Task[A] =
    op.asScalaz.foldMap(ScalazInterpreter.io(client))

  def put[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(item: T): Task[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.put(tableName)(item))

  def putAll[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(items: Set[T]): Task[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  def get[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(key: UniqueKey[_]): Task[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  def getWithConsistency[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(key: UniqueKey[_]): Task[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  def getAll[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(keys: UniqueKeys[_]): Task[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  def getAllWithConsistency[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(keys: UniqueKeys[_]): Task[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAllWithConsistency[T](tableName)(keys))

  def delete[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_]): Task[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  def deleteAll(
    client: AmazonDynamoDBAsync
  )(tableName: String)(items: UniqueKeys[_]): Task[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  def update[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(key: UniqueKey[_], expression: UpdateExpression): Task[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.update[T](tableName)(key)(expression))

  def scan[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String): Task[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  def scanWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String,
                                                                  limit: Int): Task[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit))

  def scanFrom[T: DynamoFormat](client: AmazonDynamoDBAsync)(
    tableName: String,
    limit: Int,
    startKey: Option[EvaluationKey]
  ): Task[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanFrom(tableName, limit, startKey))

  def scanIndex[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String, indexName: String): Task[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  def scanIndexWithLimit[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String, indexName: String, limit: Int): Task[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit))

  def scanIndexFrom[T: DynamoFormat](client: AmazonDynamoDBAsync)(
    tableName: String,
    indexName: String,
    limit: Int,
    startKey: Option[EvaluationKey]
  ): Task[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanIndexFrom(tableName, indexName, limit, startKey))

  def query[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(query: Query[_]): Task[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  def queryWithLimit[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String)(query: Query[_], limit: Int): Task[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit))

  def queryFrom[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
    query: Query[_],
    limit: Int,
    startKey: Option[EvaluationKey]
  ): Task[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryFrom(tableName)(query, limit, startKey))

  def queryIndex[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String, indexName: String)(query: Query[_]): Task[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))

  def queryIndexWithLimit[T: DynamoFormat](
    client: AmazonDynamoDBAsync
  )(tableName: String, indexName: String)(query: Query[_], limit: Int): Task[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit))

  def queryIndexFrom[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(
    query: Query[_],
    limit: Int,
    startKey: Option[EvaluationKey]
  ): Task[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryIndexFrom(tableName, indexName)(query, limit, startKey))

}
