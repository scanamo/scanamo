package com.gu.scanamo

import cats.effect.Effect
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, DeleteItemResult}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{CatsInterpreter, ScanamoOps}
import com.gu.scanamo.query.{Query, UniqueKey, UniqueKeys}
import com.gu.scanamo.update.UpdateExpression

object ScanamoCats {

  def exec[F[_]: Effect, A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A]): F[A] =
    op.foldMap(CatsInterpreter.effect(client))

  def put[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(item: T)
  : F[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.put(tableName)(item))

  def putAll[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(items: Set[T])
  : F[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  def get[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
  : F[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  def getWithConsistency[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
  : F[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  def getAll[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])
  : F[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  def getAllWithConsistency[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])
  : F[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAllWithConsistency[T](tableName)(keys))

  def delete[F[_]: Effect, T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
  : F[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  def deleteAll[F[_]: Effect](client: AmazonDynamoDBAsync)(tableName: String)(items: UniqueKeys[_])
  : F[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  def update[F[_]: Effect, V: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_], expression: UpdateExpression)
  : F[Either[DynamoReadError,V]] =
    exec(client)(ScanamoFree.update[V](tableName)(key)(expression))

  def scan[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)
  : F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  def scanWithLimit[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, limit: Int)
  : F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit))

  def scanIndex[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)
  : F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  def scanIndexWithLimit[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String, limit: Int)
  : F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit))

  def query[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_])
  : F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  def queryWithLimit[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_], limit: Int)
  : F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit))

  def queryIndex[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(query: Query[_])
  : F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))

  def queryIndexWithLimit[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(query: Query[_], limit: Int, startKey: Option[Map[String, AttributeValue]])
  : F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit))

}
