package com.gu.scanamo.ops

import cats.~>
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import scalaz.ioeffect.IO
import scalaz.ioeffect.ExitResult

object ScalazInterpreter {
  def io(client: AmazonDynamoDBAsync): ScanamoOpsA ~> IO[ConditionalCheckFailedException, ?] = new (ScanamoOpsA ~> IO[ConditionalCheckFailedException, ?]) {
    private def eff[A <: AmazonWebServiceRequest, B](f: (A, AsyncHandler[A, B]) => java.util.concurrent.Future[B], req: A): IO[Throwable, B] =
      IO.async { cb =>
        val handler = new AsyncHandler[A, B] {
          def onError(exception: Exception): Unit =
            cb(ExitResult.Failed(exception))

          def onSuccess(request: A, result: B): Unit =
            cb(ExitResult.Completed(result))
        }
        val _ = f(req, handler)
      }

    override def apply[A](fa: ScanamoOpsA[A]): IO[ConditionalCheckFailedException, A] = fa match {
      case Put(req) =>
        eff(client.putItemAsync, JavaRequests.put(req))
          .catchAll[ConditionalCheckFailedException](
            _ match {
             case e: ConditionalCheckFailedException => IO.fail(e)
             case t => IO.interrupt(t)
            }
          )
      case ConditionalPut(req) =>
        eff(client.putItemAsync, JavaRequests.put(req))
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
      case Get(req) =>
        eff(client.getItemAsync, req)
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )

      case Delete(req) =>
        eff(client.deleteItemAsync, JavaRequests.delete(req))
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
      case ConditionalDelete(req) =>
        eff(client.deleteItemAsync, JavaRequests.delete(req))
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
      case Scan(req) =>
        eff(client.scanAsync, JavaRequests.scan(req))
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
      case Query(req) =>
        eff(client.queryAsync, JavaRequests.query(req))
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        eff(client.batchWriteItemAsync(_: BatchWriteItemRequest, _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]), req)
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
      case BatchGet(req) =>
        eff(client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]), req)
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
      case Update(req) =>
        eff(client.updateItemAsync, JavaRequests.update(req))
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
      case ConditionalUpdate(req) =>
        eff(client.updateItemAsync, JavaRequests.update(req))
          .catchAll[ConditionalCheckFailedException](
            _ match {
              case e: ConditionalCheckFailedException => IO.fail(e)
              case t => IO.interrupt(t)
            }
          )
    }
  }
}
