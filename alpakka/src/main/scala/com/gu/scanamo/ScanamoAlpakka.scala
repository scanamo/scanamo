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

  def put[T: DynamoFormat](client: DynamoClient)(tableName: String)(item: T)
                          (implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.put(tableName)(item))

  def putAll[T: DynamoFormat](client: DynamoClient)(tableName: String)(items: Set[T])
                             (implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  def get[T: DynamoFormat](client: DynamoClient)(tableName: String)(key: UniqueKey[_])
                          (implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  def getWithConsistency[T: DynamoFormat](client: DynamoClient)(tableName: String)(key: UniqueKey[_])
                                         (implicit ec: ExecutionContext): Future[Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  def getAll[T: DynamoFormat](client: DynamoClient)(tableName: String)(keys: UniqueKeys[_])
                             (implicit ec: ExecutionContext): Future[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  def getAllWithConsistency[T: DynamoFormat](client: DynamoClient)(tableName: String)(keys: UniqueKeys[_])
                             (implicit ec: ExecutionContext): Future[Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAllWithConsistency[T](tableName)(keys))

  def delete[T](client: DynamoClient)(tableName: String)(key: UniqueKey[_])
               (implicit ec: ExecutionContext): Future[DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  def deleteAll(client: DynamoClient)(tableName: String)(items: UniqueKeys[_])
               (implicit ec: ExecutionContext): Future[List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  def update[V: DynamoFormat](client: DynamoClient)(tableName: String)(key: UniqueKey[_], expression: UpdateExpression)
                             (implicit ec: ExecutionContext): Future[Either[DynamoReadError,V]] =
    exec(client)(ScanamoFree.update[V](tableName)(key)(expression))

  def scan[T: DynamoFormat](client: DynamoClient)(tableName: String)
                           (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  def scanWithLimit[T: DynamoFormat](client: DynamoClient)(tableName: String, limit: Int, startKey: Option[EvaluationKey])
                                    (implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit, startKey))

  def scanIndex[T: DynamoFormat](client: DynamoClient)(tableName: String, indexName: String)
                                (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  def scanIndexWithLimit[T: DynamoFormat](client: DynamoClient)(tableName: String, indexName: String, limit: Int, startKey: Option[EvaluationKey])
                                         (implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit, startKey))

  def query[T: DynamoFormat](client: DynamoClient)(tableName: String)(query: Query[_])
                            (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  def queryWithLimit[T: DynamoFormat](client: DynamoClient)(tableName: String)(query: Query[_], limit: Int, startKey: Option[EvaluationKey])
                                     (implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit, startKey))

  def queryIndex[T: DynamoFormat](client: DynamoClient)(tableName: String, indexName: String)(query: Query[_])
                                 (implicit ec: ExecutionContext): Future[List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))

  def queryIndexWithLimit[T: DynamoFormat](client: DynamoClient)(tableName: String, indexName: String)(
    query: Query[_], limit: Int, startKey: Option[EvaluationKey])(implicit ec: ExecutionContext): Future[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit, startKey))
}
