package com.gu.scanamo


import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, ConditionalCheckFailedException, DeleteItemResult}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{ScalazInterpreter, ScanamoOps}
import com.gu.scanamo.query._
import com.gu.scanamo.update.UpdateExpression
import scalaz.ioeffect.IO
import shims._

object ScanamoScalaz {
  
  def exec[A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A]): IO[ConditionalCheckFailedException, A] =
    op.asScalaz.foldMap(ScalazInterpreter.io(client))

  def put[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(item: T)
  : IO[ConditionalCheckFailedException, Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.put(tableName)(item))

  def putAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(items: Set[T])
  : IO[ConditionalCheckFailedException, List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  def get[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
  : IO[ConditionalCheckFailedException, Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  def getWithConsistency[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
  : IO[ConditionalCheckFailedException, Option[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  def getAll[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])
  : IO[ConditionalCheckFailedException, Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAll[T](tableName)(keys))

  def getAllWithConsistency[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(keys: UniqueKeys[_])
  : IO[ConditionalCheckFailedException, Set[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.getAllWithConsistency[T](tableName)(keys))

  def delete[T](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_])
  : IO[ConditionalCheckFailedException, DeleteItemResult] =
    exec(client)(ScanamoFree.delete(tableName)(key))

  def deleteAll(client: AmazonDynamoDBAsync)(tableName: String)(items: UniqueKeys[_])
  : IO[ConditionalCheckFailedException, List[BatchWriteItemResult]] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  def update[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(key: UniqueKey[_], expression: UpdateExpression)
  : IO[ConditionalCheckFailedException, Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.update[T](tableName)(key)(expression))

  def scan[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)
  : IO[ConditionalCheckFailedException, List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scan(tableName))

  def scanWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, limit: Int, startKey: Option[EvaluationKey])
  : IO[ConditionalCheckFailedException, (List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit, startKey))

  def scanIndex[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)
  : IO[ConditionalCheckFailedException, List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  def scanIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String, limit: Int, startKey: Option[EvaluationKey])
  : IO[ConditionalCheckFailedException, (List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit, startKey))

  def query[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_])
  : IO[ConditionalCheckFailedException, List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  def queryWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String)(query: Query[_], limit: Int, startKey: Option[EvaluationKey])
  : IO[ConditionalCheckFailedException, (List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit, startKey))

  def queryIndex[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(query: Query[_])
  : IO[ConditionalCheckFailedException, List[Either[DynamoReadError, T]]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))

  def queryIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDBAsync)(tableName: String, indexName: String)(query: Query[_], limit: Int, startKey: Option[EvaluationKey])
  : IO[ConditionalCheckFailedException, (List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit, startKey))

}
