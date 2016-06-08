package com.gu.scanamo.ops

import cats.data.Xor
import cats.{Id, ~>}
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBAsync}
import com.gu.scanamo.request.{ScanamoDeleteRequest, ScanamoPutRequest, ScanamoUpdateRequest}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

sealed trait ScanamoOpsA[A] extends Product with Serializable
final case class Put(req: ScanamoPutRequest) extends ScanamoOpsA[PutItemResult]
final case class ConditionalPut(req: ScanamoPutRequest) extends ScanamoOpsA[Xor[ConditionalCheckFailedException, PutItemResult]]
final case class Get(req: GetItemRequest) extends ScanamoOpsA[GetItemResult]
final case class Delete(req: ScanamoDeleteRequest) extends ScanamoOpsA[DeleteItemResult]
final case class ConditionalDelete(req: ScanamoDeleteRequest) extends ScanamoOpsA[Xor[ConditionalCheckFailedException, DeleteItemResult]]
final case class Scan(req: ScanRequest) extends ScanamoOpsA[ScanResult]
final case class Query(req: QueryRequest) extends ScanamoOpsA[QueryResult]
final case class BatchWrite(req: BatchWriteItemRequest) extends ScanamoOpsA[BatchWriteItemResult]
final case class BatchGet(req: BatchGetItemRequest) extends ScanamoOpsA[BatchGetItemResult]
final case class Update(req: ScanamoUpdateRequest) extends ScanamoOpsA[UpdateItemResult]
final case class ConditionalUpdate(req: ScanamoUpdateRequest) extends ScanamoOpsA[Xor[ConditionalCheckFailedException, UpdateItemResult]]

object ScanamoOps {

  import cats.free.Free.liftF

  def put(req: ScanamoPutRequest): ScanamoOps[PutItemResult] = liftF[ScanamoOpsA, PutItemResult](Put(req))
  def conditionalPut(req: ScanamoPutRequest): ScanamoOps[Xor[ConditionalCheckFailedException, PutItemResult]] =
    liftF[ScanamoOpsA, Xor[ConditionalCheckFailedException, PutItemResult]](ConditionalPut(req))
  def get(req: GetItemRequest): ScanamoOps[GetItemResult] = liftF[ScanamoOpsA, GetItemResult](Get(req))
  def delete(req: ScanamoDeleteRequest): ScanamoOps[DeleteItemResult] = liftF[ScanamoOpsA, DeleteItemResult](Delete(req))
  def conditionalDelete(req: ScanamoDeleteRequest): ScanamoOps[Xor[ConditionalCheckFailedException, DeleteItemResult]] =
    liftF[ScanamoOpsA, Xor[ConditionalCheckFailedException, DeleteItemResult]](ConditionalDelete(req))
  def scan(req: ScanRequest): ScanamoOps[ScanResult] = liftF[ScanamoOpsA, ScanResult](Scan(req))
  def query(req: QueryRequest): ScanamoOps[QueryResult] = liftF[ScanamoOpsA, QueryResult](Query(req))
  def batchWrite(req: BatchWriteItemRequest): ScanamoOps[BatchWriteItemResult] =
    liftF[ScanamoOpsA, BatchWriteItemResult](BatchWrite(req))
  def batchGet(req: BatchGetItemRequest): ScanamoOps[BatchGetItemResult] =
    liftF[ScanamoOpsA, BatchGetItemResult](BatchGet(req))
  def update(req: ScanamoUpdateRequest): ScanamoOps[UpdateItemResult] =
    liftF[ScanamoOpsA, UpdateItemResult](Update(req))
  def conditionalUpdate(req: ScanamoUpdateRequest): ScanamoOps[Xor[ConditionalCheckFailedException, UpdateItemResult]] =
    liftF[ScanamoOpsA, Xor[ConditionalCheckFailedException, UpdateItemResult]](ConditionalUpdate(req))
}

object ScanamoInterpreters {
  import collection.convert.decorateAsJava._

  def javaPutRequest(req: ScanamoPutRequest): PutItemRequest =
    req.condition.foldLeft(
      new PutItemRequest().withTableName(req.tableName).withItem(req.item)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(c.attributeNames.asJava)
      )((cond, values) => cond.withExpressionAttributeValues(values.asJava))
    )

  def javaDeleteRequest(req: ScanamoDeleteRequest): DeleteItemRequest =
    req.condition.foldLeft(
      new DeleteItemRequest().withTableName(req.tableName).withKey(req.key)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(c.attributeNames.asJava)
      )((cond, values) => cond.withExpressionAttributeValues(values.asJava))
    )

  def javaUpdateRequest(req: ScanamoUpdateRequest): UpdateItemRequest =
    req.condition.foldLeft(
      new UpdateItemRequest().withTableName(req.tableName).withKey(req.key)
        .withUpdateExpression(req.updateExpression)
        .withExpressionAttributeNames(req.attributeNames.asJava)
        .withExpressionAttributeValues(req.attributeValues.asJava)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(
          (c.attributeNames ++ req.attributeNames).asJava)
      )((cond, values) => cond.withExpressionAttributeValues(
        (values ++ req.attributeValues).asJava))
    )

  def id(client: AmazonDynamoDB) = new (ScanamoOpsA ~> Id) {
    def apply[A](op: ScanamoOpsA[A]): Id[A] = op match {
      case Put(req) =>
        client.putItem(javaPutRequest(req))
      case ConditionalPut(req) =>
        Xor.catchOnly[ConditionalCheckFailedException] {
          client.putItem(javaPutRequest(req))
        }
      case Get(req) =>
        client.getItem(req)
      case Delete(req) =>
        client.deleteItem(javaDeleteRequest(req))
      case ConditionalDelete(req) =>
        Xor.catchOnly[ConditionalCheckFailedException] {
          client.deleteItem(javaDeleteRequest(req))
        }
      case Scan(req) =>
        client.scan(req)
      case Query(req) =>
        client.query(req)
      case BatchWrite(req) =>
        client.batchWriteItem(req)
      case BatchGet(req) =>
        client.batchGetItem(req)
      case Update(req) =>
        client.updateItem(javaUpdateRequest(req))
      case ConditionalUpdate(req) =>
        Xor.catchOnly[ConditionalCheckFailedException] {
          client.updateItem(javaUpdateRequest(req))
        }
    }
  }

  def future(client: AmazonDynamoDBAsync)(implicit ec: ExecutionContext) = new (ScanamoOpsA ~> Future) {
    private def futureOf[X <: AmazonWebServiceRequest, T](call: (X,AsyncHandler[X, T]) => java.util.concurrent.Future[T], req: X): Future[T] = {
      val p = Promise[T]()
      val h = new AsyncHandler[X, T] {
        def onError(exception: Exception) { p.complete(Failure(exception)); () }
        def onSuccess(request: X, result: T) { p.complete(Success(result)); () }
      }
      call(req, h)
      p.future
    }

    override def apply[A](op: ScanamoOpsA[A]): Future[A] = op match {
      case Put(req) =>
        futureOf(client.putItemAsync, javaPutRequest(req))
      case ConditionalPut(req) =>
        futureOf(client.putItemAsync, javaPutRequest(req)).map(Xor.right[ConditionalCheckFailedException, PutItemResult]).recover {
          case e: ConditionalCheckFailedException => Xor.left(e)
        }
      case Get(req) =>
        futureOf(client.getItemAsync, req)
      case Delete(req) =>
        futureOf(client.deleteItemAsync, javaDeleteRequest(req))
      case ConditionalDelete(req) =>
        futureOf(client.deleteItemAsync, javaDeleteRequest(req)).map(Xor.right[ConditionalCheckFailedException, DeleteItemResult]).recover {
          case e: ConditionalCheckFailedException => Xor.left(e)
        }
      case Scan(req) =>
        futureOf(client.scanAsync, req)
      case Query(req) =>
        futureOf(client.queryAsync, req)
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        futureOf(client.batchWriteItemAsync(_: BatchWriteItemRequest, _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]), req)
      case BatchGet(req) =>
        futureOf(client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]), req)
      case Update(req) =>
        futureOf(client.updateItemAsync, javaUpdateRequest(req))
      case ConditionalUpdate(req) =>
        futureOf(client.updateItemAsync, javaUpdateRequest(req)).map(Xor.right[ConditionalCheckFailedException, UpdateItemResult]).recover {
          case e: ConditionalCheckFailedException => Xor.left(e)
        }
    }
  }
}

