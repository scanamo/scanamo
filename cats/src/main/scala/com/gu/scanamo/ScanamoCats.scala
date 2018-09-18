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

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.put]]", "1.0")
  def put[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      item: T): F[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.put(tableName)(item))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.putAll]]", "1.0")
  def putAll[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      items: Set[T]): F[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.get]]", "1.0")
  def get[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      key: UniqueKey[_]): F[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.consistently]]", "1.0")
  def getWithConsistency[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      key: UniqueKey[_]): F[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.getAll]]", "1.0")
  def getAll[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      keys: UniqueKeys[_]): F[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.getAll]] and [[com.gu.scanamo.Table.consistently]]", "1.0")
  def getAllWithConsistency[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      keys: UniqueKeys[_]): F[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAllWithConsistency[T](tableName)(keys))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.delete]]", "1.0")
  def delete[F[_]: Effect, T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_]): F[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.deleteAll]]", "1.0")
  def deleteAll[F[_]: Effect](client: AmazonDynamoDBAsync)(tableName: String)(
      items: UniqueKeys[_]): F[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.update]]", "1.0")
  def update[F[_]: Effect, V: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String)(key: UniqueKey[_], expression: UpdateExpression): F[Either[DynamoReadError, V]] =
    exec(client)(ScanamoFree.update[V](tableName)(key)(expression))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.scan]]", "1.0")
  def scan[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String): F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.limit]]", "1.0")
  def scanWithLimit[F[_]: Effect, T: DynamoFormat](
      client: AmazonDynamoDBAsync)(tableName: String, limit: Int): F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.scanFrom]]", "1.0")
  def scanFrom[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String,
      limit: Int,
      startKey: Option[EvaluationKey]): F[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanFrom(tableName, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]]", "1.0")
  def scanIndex[F[_]: Effect, T: DynamoFormat](
      client: AmazonDynamoDBAsync)(tableName: String, indexName: String): F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]] and [[com.gu.scanamo.SecondaryIndex.limit]]", "1.0")
  def scanIndexWithLimit[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String,
      indexName: String,
      limit: Int): F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.scanFrom]] and [[com.gu.scanamo.Table.index]]", "1.0")
  def scanIndexFrom[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String,
      indexName: String,
      limit: Int,
      startKey: Option[EvaluationKey]): F[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanIndexFrom(tableName, indexName, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.query]]", "1.0")
  def query[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      query: Query[_]): F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.limit]]", "1.0")
  def queryWithLimit[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String)(query: Query[_], limit: Int): F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.queryFrom]]", "1.0")
  def queryFrom[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(
      query: Query[_],
      limit: Int,
      startKey: Option[EvaluationKey]): F[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryFrom(tableName)(query, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]]", "1.0")
  def queryIndex[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(
      query: Query[_]): F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]] and [[com.gu.scanamo.SecondaryIndex.limit]]", "1.0")
  def queryIndexWithLimit[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(
      tableName: String,
      indexName: String)(query: Query[_], limit: Int): F[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.queryFrom]] and [[com.gu.scanamo.Table.index]]", "1.0")
  def queryIndexFrom[F[_]: Effect, T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(
      query: Query[_],
      limit: Int,
      startKey: Option[EvaluationKey]): F[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryIndexFrom(tableName, indexName)(query, limit, startKey))

}
