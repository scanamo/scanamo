package org.scanamo.ops

import java.util.concurrent.CompletionException

import cats.effect.Async
import cats.implicits._
import cats.{Monad, ~>}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{ConditionalCheckFailedException, DynamoDbRequest}

import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object CatsInterpreter {
  def effect[F[_]: Monad](client: DynamoDbAsyncClient)(implicit F: Async[F], ec: ExecutionContext): ScanamoOpsA ~> F = new (ScanamoOpsA ~> F) {
    private def eff[A <: DynamoDbRequest, B]
    (
      f: java.util.concurrent.CompletionStage[B]
    ): F[B] = {
      F.async { cb =>
        FutureConverters.toScala(f).onComplete {
          case Success(v) => cb(Right(v))
          case Failure(e: CompletionException) ⇒ cb(Left(e.getCause))
          case Failure(e) ⇒ cb(Left(e))
        }
      }
    }

    override def apply[A](fa: ScanamoOpsA[A]): F[A] = fa match {
      case Put(req) =>
        eff(client.putItem( JavaRequests.put(req)))
      case ConditionalPut(req) =>
        eff(client.putItem(JavaRequests.put(req))).attempt
          .flatMap(
            _.fold(
              _ match {
                case e: ConditionalCheckFailedException => F.delay(Left(e))
                case t                                  => F.raiseError(t) // raise error as opposed to swallowing
              },
              a => F.delay(Right(a))
            )
          )
      case Get(req) =>
        eff(client.getItem(req))
      case Delete(req) =>
        eff(client.deleteItem(JavaRequests.delete(req)))
      case ConditionalDelete(req) =>
        eff(client.deleteItem(JavaRequests.delete(req))).attempt
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
        eff(client.scan(JavaRequests.scan(req)))
      case Query(req) =>
        eff(client.query(JavaRequests.query(req)))
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        eff(client.batchWriteItem(req))
      case BatchGet(req) =>
        eff(client.batchGetItem(req))
      case Update(req) =>
        eff(client.updateItem(JavaRequests.update(req)))
      case ConditionalUpdate(req) =>
        eff(client.updateItem(JavaRequests.update(req))).attempt
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
