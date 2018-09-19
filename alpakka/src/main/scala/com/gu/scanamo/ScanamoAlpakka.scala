package com.gu.scanamo

import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, DeleteItemResult}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{AlpakkaInterpreter, ScanamoOps}
import com.gu.scanamo.query.{Query, UniqueKey, UniqueKeys}
import com.gu.scanamo.update.UpdateExpression

import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides the same interface as [[com.gu.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client
  * and an implicit [[scala.concurrent.ExecutionContext]] and returns a [[scala.concurrent.Future]]
  */
object ScanamoAlpakka {
  import cats.instances.future._

  def exec[A](client: DynamoClient)(op: ScanamoOps[A])(implicit ec: ExecutionContext) =
    op.foldMap(AlpakkaInterpreter.future(client)(ec))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.put]]", "1.0")
  def put[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String)(item: T)(implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.put(tableName)(item))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.putAll]]", "1.0")
  def putAll[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String)(items: Set[T])(implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.get]]", "1.0")
  def get[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String)(key: UniqueKey[_])(implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.consistently]]", "1.0")
  def getWithConsistency[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String)(key: UniqueKey[_])(implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.getAll]]", "1.0")
  def getAll[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String)(keys: UniqueKeys[_])(implicit ec: ExecutionContext): Future[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.getAll]] and [[com.gu.scanamo.Table.consistently]]", "1.0")
  def getAllWithConsistency[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String)(keys: UniqueKeys[_])(implicit ec: ExecutionContext): Future[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAllWithConsistency[T](tableName)(keys))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.delete]]", "1.0")
  def delete[T](
    client: DynamoClient
  )(tableName: String)(key: UniqueKey[_])(implicit ec: ExecutionContext): Future[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.deleteAll]]", "1.0")
  def deleteAll(
    client: DynamoClient
  )(tableName: String)(items: UniqueKeys[_])(implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.update]]", "1.0")
  def update[V: DynamoFormat](client: DynamoClient)(tableName: String)(key: UniqueKey[_], expression: UpdateExpression)(
    implicit ec: ExecutionContext
  ): Future[Either[DynamoReadError, V]] =
    exec(client)(ScanamoFree.update[V](tableName)(key)(expression))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.scan]]", "1.0")
  def scan[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String)(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.limit]]", "1.0")
  def scanWithLimit[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String, limit: Int)(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.scanFrom]]", "1.0")
  def scanFrom[T: DynamoFormat](client: DynamoClient)(tableName: String, limit: Int, startKey: Option[EvaluationKey])(
    implicit ec: ExecutionContext
  ): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanFrom(tableName, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]]", "1.0")
  def scanIndex[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String, indexName: String)(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]] and [[com.gu.scanamo.SecondaryIndex.limit]]", "1.0")
  def scanIndexWithLimit[T: DynamoFormat](client: DynamoClient)(tableName: String, indexName: String, limit: Int)(
    implicit ec: ExecutionContext
  ): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.scanFrom]] and [[com.gu.scanamo.Table.index]]", "1.0")
  def scanIndexFrom[T: DynamoFormat](client: DynamoClient)(
    tableName: String,
    indexName: String,
    limit: Int,
    startKey: Option[EvaluationKey]
  )(implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanIndexFrom(tableName, indexName, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.query]]", "1.0")
  def query[T: DynamoFormat](
    client: DynamoClient
  )(tableName: String)(query: Query[_])(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.limit]]", "1.0")
  def queryWithLimit[T: DynamoFormat](client: DynamoClient)(
    tableName: String
  )(query: Query[_], limit: Int)(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.queryFrom]]", "1.0")
  def queryFrom[T: DynamoFormat](client: DynamoClient)(tableName: String)(
    query: Query[_],
    limit: Int,
    startKey: Option[EvaluationKey]
  )(implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryFrom(tableName)(query, limit, startKey))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]]", "1.0")
  def queryIndex[T: DynamoFormat](client: DynamoClient)(tableName: String, indexName: String)(
    query: Query[_]
  )(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.index]] and [[com.gu.scanamo.SecondaryIndex.limit]]", "1.0")
  def queryIndexWithLimit[T: DynamoFormat](client: DynamoClient)(
    tableName: String,
    indexName: String
  )(query: Query[_], limit: Int)(implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit))

  @deprecated("Use [[exec]] with [[com.gu.scanamo.Table.queryFrom]] and [[com.gu.scanamo.Table.index]]", "1.0")
  def queryIndexFrom[T: DynamoFormat](client: DynamoClient)(tableName: String, indexName: String)(
    query: Query[_],
    limit: Int,
    startKey: Option[EvaluationKey]
  )(implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryIndexFrom(tableName, indexName)(query, limit, startKey))
}
