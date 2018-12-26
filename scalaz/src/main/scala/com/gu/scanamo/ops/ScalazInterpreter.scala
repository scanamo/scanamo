package org.scanamo.ops

import java.util.concurrent.{Future => JFuture}

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._
import scalaz.ioeffect.{ExitResult, IO, Task}
import scalaz.~>

object ScalazInterpreter {
  def io(client: AmazonDynamoDBAsync) = new (ScanamoOpsA ~> Task) {
    private[this] def eff[A <: AmazonWebServiceRequest, B](f: (A, AsyncHandler[A, B]) => JFuture[B], req: A): Task[B] =
      IO.async { cb =>
        val handler = new AsyncHandler[A, B] {
          def onError(exception: Exception): Unit =
            cb(ExitResult.Failed(exception))

          def onSuccess(request: A, result: B): Unit =
            cb(ExitResult.Completed(result))
        }
        val _ = f(req, handler)
      }

    private[this] def catchConditional[A, A0](io: Task[A0]): Task[A] =
      io.map(Right(_): Either[ConditionalCheckFailedException, A0])
        .catchSome {
          case e: ConditionalCheckFailedException => IO.now(Left(e))
        }
        .asInstanceOf[Task[A]]

    override def apply[A](fa: ScanamoOpsA[A]): Task[A] =
      fa match {
        case Put(req) =>
          eff(client.putItemAsync, JavaRequests.put(req))
        case ConditionalPut(req) =>
          catchConditional(eff(client.putItemAsync, JavaRequests.put(req)))
        case Get(req) =>
          eff(client.getItemAsync, req)
        case Delete(req) =>
          eff(client.deleteItemAsync, JavaRequests.delete(req))
        case ConditionalDelete(req) =>
          catchConditional(eff(client.deleteItemAsync, JavaRequests.delete(req)))
        case Scan(req) =>
          eff(client.scanAsync, JavaRequests.scan(req))
        case Query(req) =>
          eff(client.queryAsync, JavaRequests.query(req))
        // Overloading means we need explicit parameter types here
        case BatchWrite(req) =>
          eff(
            client.batchWriteItemAsync(
              _: BatchWriteItemRequest,
              _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]
            ),
            req
          )
        case BatchGet(req) =>
          eff(
            client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]),
            req
          )
        case Update(req) =>
          eff(client.updateItemAsync, JavaRequests.update(req))
        case ConditionalUpdate(req) =>
          catchConditional(eff(client.updateItemAsync, JavaRequests.update(req)))
      }
  }
}
