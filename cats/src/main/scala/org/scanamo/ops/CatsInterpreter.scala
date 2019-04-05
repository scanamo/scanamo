package org.scanamo.ops

import cats.effect.Async
import cats.implicits._
import cats.~>
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model._

object CatsInterpreter {
  def effect[F[_]](client: AmazonDynamoDBAsync)(implicit F: Async[F]): ScanamoOpsA ~> F = new (ScanamoOpsA ~> F) {
    private def eff[A <: AmazonWebServiceRequest, B](
      f: (A, AsyncHandler[A, B]) => java.util.concurrent.Future[B],
      req: A
    ): F[B] =
      F.async { cb =>
        val handler = new AsyncHandler[A, B] {
          def onError(exception: Exception): Unit =
            cb(Left(exception))

          def onSuccess(request: A, result: B): Unit =
            cb(Right(result))
        }
        val _ = f(req, handler)
      }

    override def apply[A](fa: ScanamoOpsA[A]): F[A] = fa match {
      case Put(req) =>
        eff(client.putItemAsync, JavaRequests.put(req)).attempt
          .flatMap(
            _.fold(
              _ match {
                case e: AmazonDynamoDBException => F.delay(Left(e))
                case t                          => F.raiseError(t) // raise error as opposed to swallowing
              },
              a => F.delay(Right(a))
            )
          )
      case Get(req) =>
        eff(client.getItemAsync, req).attempt
          .flatMap(
            _.fold(
              _ match {
                case e: AmazonDynamoDBException => F.delay(Left(e))
                case t                          => F.raiseError(t) // raise error as opposed to swallowing
              },
              a => F.delay(Right(a))
            )
          )
      case Delete(req) =>
        eff(client.deleteItemAsync, JavaRequests.delete(req))
      case ConditionalDelete(req) =>
        eff(client.deleteItemAsync, JavaRequests.delete(req)).attempt
          .flatMap(
            _.fold(
              _ match {
                case e: ConditionalCheckFailedException => F.delay(Left(e))
                case t                                  => F.raiseError(t) // raise error as opposed to swallowing
              },
              a => F.delay(Right(a))
            )
          )
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
        eff(client.updateItemAsync, JavaRequests.update(req)).attempt
          .flatMap(
            _.fold(
              _ match {
                case e: ConditionalCheckFailedException => F.delay(Left(e))
                case t                                  => F.raiseError(t) // raise error as opposed to swallowing
              },
              a => F.delay(Right(a))
            )
          )
    }
  }
}
