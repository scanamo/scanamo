package com.gu.scanamo.ops

import java.util.concurrent.{Future => JFuture}

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import scalaz.ioeffect.{ExitResult, IO}
import scalaz.~>

object ScalazInterpreter {
  def io(client: AmazonDynamoDBAsync) = new (ScanamoOpsA ~> IO[ConditionalCheckFailedException, ?]) {
    private[this] def eff[A <: AmazonWebServiceRequest, B](f: (A, AsyncHandler[A, B]) => JFuture[B], req: A): IO[Exception, B] =
      IO.async { cb =>
        val handler = new AsyncHandler[A, B] {
          def onError(exception: Exception): Unit =
            cb(ExitResult.Failed(exception))

          def onSuccess(request: A, result: B): Unit =
            cb(ExitResult.Completed(result))
        }
        val _ = f(req, handler)
      }

    // Refactor this with Leibniz when Scala solves the GADT problem
    private[this] def unifyA[A0, A](io: IO[Exception, A0]): IO[ConditionalCheckFailedException, A] =
      io.catchAll[ConditionalCheckFailedException] {
        case e: ConditionalCheckFailedException => IO.fail(e)
        case t => IO.interrupt(t)
      }.asInstanceOf[IO[ConditionalCheckFailedException, A]]

    override def apply[A](fa: ScanamoOpsA[A]): IO[ConditionalCheckFailedException, A] = {
      fa match {
        case Put(req) =>
          unifyA(eff(client.putItemAsync, JavaRequests.put(req)))
        case ConditionalPut(req) =>
          unifyA(eff(client.putItemAsync, JavaRequests.put(req)))
        case Get(req) =>
          unifyA(eff(client.getItemAsync, req))
        case Delete(req) =>
          unifyA(eff(client.deleteItemAsync, JavaRequests.delete(req)))
        case ConditionalDelete(req) =>
          unifyA(eff(client.deleteItemAsync, JavaRequests.delete(req)))
        case Scan(req) =>
          unifyA(eff(client.scanAsync, JavaRequests.scan(req)))
        case Query(req) =>
          unifyA(eff[QueryRequest, QueryResult](client.queryAsync, JavaRequests.query(req)))
        // Overloading means we need explicit parameter types here
        case BatchWrite(req) =>
          unifyA(eff(
            client.batchWriteItemAsync(
              _: BatchWriteItemRequest,
              _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]),
            req
          ))
        case BatchGet(req) =>
          unifyA(eff(
            client.batchGetItemAsync(
              _: BatchGetItemRequest,
              _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]),
            req
          ))
        case Update(req) =>
          unifyA(eff(client.updateItemAsync, JavaRequests.update(req)))
        case ConditionalUpdate(req) =>
          unifyA(eff[UpdateItemRequest, UpdateItemResult](client.updateItemAsync, JavaRequests.update(req)))

      }
    }
  }
}
