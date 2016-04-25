package com.gu.scanamo.ops

import cats.{Id, ~>}
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBAsync}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

sealed trait ScanamoOpsA[A]
final case class Put(req: PutItemRequest) extends ScanamoOpsA[PutItemResult]
final case class Get(req: GetItemRequest) extends ScanamoOpsA[GetItemResult]
final case class Delete(req: DeleteItemRequest) extends ScanamoOpsA[DeleteItemResult]
final case class Scan(req: ScanRequest) extends ScanamoOpsA[ScanResult]
final case class Query(req: QueryRequest) extends ScanamoOpsA[QueryResult]
final case class BatchWrite(req: BatchWriteItemRequest) extends ScanamoOpsA[BatchWriteItemResult]
final case class BatchGet(req: BatchGetItemRequest) extends ScanamoOpsA[BatchGetItemResult]

object ScanamoOps {
  import cats.free.Free.liftF

  def put(req: PutItemRequest): ScanamoOps[PutItemResult] = liftF[ScanamoOpsA, PutItemResult](Put(req))
  def get(req: GetItemRequest): ScanamoOps[GetItemResult] = liftF[ScanamoOpsA, GetItemResult](Get(req))
  def delete(req: DeleteItemRequest): ScanamoOps[DeleteItemResult] = liftF[ScanamoOpsA, DeleteItemResult](Delete(req))
  def scan(req: ScanRequest): ScanamoOps[ScanResult] = liftF[ScanamoOpsA, ScanResult](Scan(req))
  def query(req: QueryRequest): ScanamoOps[QueryResult] = liftF[ScanamoOpsA, QueryResult](Query(req))
  def batchWrite(req: BatchWriteItemRequest): ScanamoOps[BatchWriteItemResult] =
    liftF[ScanamoOpsA, BatchWriteItemResult](BatchWrite(req))
  def batchGet(req: BatchGetItemRequest): ScanamoOps[BatchGetItemResult] =
    liftF[ScanamoOpsA, BatchGetItemResult](BatchGet(req))
}

object ScanamoInterpreters {

  def id(client: AmazonDynamoDB) = new (ScanamoOpsA ~> Id) {
    def apply[A](op: ScanamoOpsA[A]): Id[A] = op match {
      case Put(req) =>
        client.putItem(req)
      case Get(req) =>
        client.getItem(req)
      case Delete(req) =>
        client.deleteItem(req)
      case Scan(req) =>
        client.scan(req)
      case Query(req) =>
        client.query(req)
      case BatchWrite(req) =>
        client.batchWriteItem(req)
      case BatchGet(req) =>
        client.batchGetItem(req)
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
        futureOf(client.putItemAsync, req)
      case Get(req) =>
        futureOf(client.getItemAsync, req)
      case Delete(req) =>
        futureOf(client.deleteItemAsync, req)
      case Scan(req) =>
        futureOf(client.scanAsync, req)
      case Query(req) =>
        futureOf(client.queryAsync, req)
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        futureOf(client.batchWriteItemAsync(_: BatchWriteItemRequest, _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]), req)
      case BatchGet(req) =>
        futureOf(client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]), req)
    }
  }
}

