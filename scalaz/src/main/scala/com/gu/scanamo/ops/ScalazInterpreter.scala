package com.gu.scanamo.ops

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import scalaz.ioeffect.IO
import scalaz.ioeffect.ExitResult
import scalaz.~>

import java.util.concurrent.{Future => JFuture}

object ScalazInterpreter {
  def io(client: AmazonDynamoDBAsync) = new (ScanamoOpsA ~> IO[Throwable, ?]) {
    private def eff[A <: AmazonWebServiceRequest, B](f: (A, AsyncHandler[A, B]) => JFuture[B], req: A): IO[Throwable, B] =
      IO.async { cb =>
        val handler = new AsyncHandler[A, B] {
          def onError(exception: Exception): Unit =
            cb(ExitResult.Failed(exception))

          def onSuccess(request: A, result: B): Unit =
            cb(ExitResult.Completed(result))
        }
        val _ = f(req, handler)
      }

    // Welcome to Scala's GADT support :D
    override def apply[A](fa: ScanamoOpsA[A]): IO[Throwable, A] = fa match {
      case Put(req) =>
        eff[PutItemRequest, PutItemResult](client.putItemAsync, JavaRequests.put(req)).asInstanceOf[IO[Throwable, A]]
      case ConditionalPut(req) =>
        eff[PutItemRequest, PutItemResult](client.putItemAsync, JavaRequests.put(req)).asInstanceOf[IO[Throwable, A]]
      case Get(req) =>
        eff[GetItemRequest, GetItemResult](client.getItemAsync, req).asInstanceOf[IO[Throwable, A]]
      case Delete(req) =>
        eff[DeleteItemRequest, DeleteItemResult](client.deleteItemAsync, JavaRequests.delete(req)).asInstanceOf[IO[Throwable, A]]
      case ConditionalDelete(req) =>
        eff[DeleteItemRequest, DeleteItemResult](client.deleteItemAsync, JavaRequests.delete(req)).asInstanceOf[IO[Throwable, A]]
      case Scan(req) =>
        eff[ScanRequest, ScanResult](client.scanAsync, JavaRequests.scan(req)).asInstanceOf[IO[Throwable, A]]
      case Query(req) =>
        eff[QueryRequest, QueryResult](client.queryAsync, JavaRequests.query(req)).asInstanceOf[IO[Throwable, A]]
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        eff[BatchWriteItemRequest, BatchWriteItemResult](
          client.batchWriteItemAsync(
            _: BatchWriteItemRequest,
            _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]),
          req
        ).asInstanceOf[IO[Throwable, A]]
      case BatchGet(req) =>
        eff[BatchGetItemRequest, BatchGetItemResult](
          client.batchGetItemAsync(
            _: BatchGetItemRequest,
            _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]),
          req
        ).asInstanceOf[IO[Throwable, A]]
      case Update(req) =>
        eff[UpdateItemRequest, UpdateItemResult](client.updateItemAsync, JavaRequests.update(req)).asInstanceOf[IO[Throwable, A]]
      case ConditionalUpdate(req) =>
        eff[UpdateItemRequest, UpdateItemResult](client.updateItemAsync, JavaRequests.update(req)).asInstanceOf[IO[Throwable, A]]

    }
  }
}
