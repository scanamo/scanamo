package org.scanamo.ops

import java.util.concurrent.CompletionStage

import cats.~>
import scalaz.zio.ExitResult.Cause
import scalaz.zio.{ExitResult, IO}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.compat.java8.FutureConverters
import scala.concurrent.java8.FuturesConvertersImpl._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

object ZioInterpreter {
  def effect(client: DynamoDbAsyncClient)(implicit ec: ExecutionContext): ScanamoOpsA ~> IO[DynamoDbException, ?] =
    new (ScanamoOpsA ~> IO[DynamoDbException, ?]) {
      private def eff[A <: DynamoDbRequest, B](
        f: A => java.util.concurrent.CompletionStage[B],
        req: A
      ): IO[DynamoDbException, B] =
        IO.async[DynamoDbException, B] { cb =>


          val a = FutureConverters.toScala(f(req))
            .transform {
              case scala.util.Success(result) =>
                Success(cb(ExitResult.succeeded(result)))
              case scala.util.Failure(exception: DynamoDbException) =>
                Success(cb(ExitResult.failed(Cause.checked(exception))))
              case scala.util.Failure(exception) =>
                Success(cb(ExitResult.failed(Cause.unchecked(exception))))
            }


          IO.fromTry(a)

          val _ = f(req, handler)
        }

      def apply[A](op: ScanamoOpsA[A]): IO[DynamoDbException, A] = op match {
        case Put(req) =>
          eff(client.putItem, JavaRequests.put(req))
        case ConditionalPut(req) =>
          eff(client.putItem, JavaRequests.put(req)).redeem(
            _ match {
              case e: ConditionalCheckFailedException => IO.now(Left(e))
              case t                                  => IO.fail(t)
            },
            a => IO.now(Right(a))
          )
        case Get(req) =>
          eff(client.getItem, req)
        case Delete(req) =>
          eff(client.deleteItem, JavaRequests.delete(req))
        case ConditionalDelete(req) =>
          eff(client.deleteItem, JavaRequests.delete(req)).redeem(
            _ match {
              case e: ConditionalCheckFailedException => IO.now(Left(e))
              case t                                  => IO.fail(t)
            },
            a => IO.now(Right(a))
          )
        case Scan(req) =>
          eff(client.scan, JavaRequests.scan(req))
        case Query(req) =>
          eff(client.query, JavaRequests.query(req))
        case BatchWrite(req) =>
          eff(
            client.batchWriteItem(
              _: BatchWriteItemRequest,
              _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResponse]
            ),
            req
          )
        case BatchGet(req) =>
          eff(
            client.batchGetItem(_: BatchGetItemRequest),
            req
          )
        case Update(req) =>
          eff(client.updateItem, JavaRequests.update(req))
        case ConditionalUpdate(req) =>
          eff(client.updateItem, JavaRequests.update(req)).redeem(
            _ match {
              case e: ConditionalCheckFailedException => IO.now(Left(e))
              case t                                  => IO.fail(t)
            },
            a => IO.now(Right(a))
          )
      }
    }
}
