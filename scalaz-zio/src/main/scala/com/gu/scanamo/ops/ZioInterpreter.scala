package org.scanamo.ops

import java.util.concurrent.CompletionException

import cats.~>
import scalaz.zio.{ExitResult, IO}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}

import scala.util.{Failure, Success}

object ZioInterpreter {
  def effect(client: DynamoDbAsyncClient)(implicit ec: ExecutionContext): ScanamoOpsA ~> IO[DynamoDbException, ?] =
    new (ScanamoOpsA ~> IO[DynamoDbException, ?]) {
      private def eff[A <: DynamoDbRequest, B](
        f: java.util.concurrent.CompletionStage[B],
      ): IO[DynamoDbException, B] =
        IO.fromFuture(ec)(() => FutureConverters.toScala(f)).redeem( {
          case e: DynamoDbException => IO.fail(e)
          case t: Throwable => IO.terminate(t)
        }, IO.now)

      def apply[A](op: ScanamoOpsA[A]): IO[DynamoDbException, A] = op match {
        case Put(req) =>
          eff(client.putItem(JavaRequests.put(req)))
        case ConditionalPut(req) =>
          eff(client.putItem(JavaRequests.put(req))).redeem(
            {
              case e : ConditionalCheckFailedException => IO.now(Left(e))
              case t                                  => IO.fail(t)
            },
            resp => IO.now(Right(resp))
          )
        case Get(req) =>
          eff(client.getItem(req))
        case Delete(req) =>
          eff(client.deleteItem(JavaRequests.delete(req)))
        case ConditionalDelete(req) =>
          eff(client.deleteItem(JavaRequests.delete(req))).redeem(
            {
              case e : ConditionalCheckFailedException => IO.now(Left(e))
              case t                                  => IO.fail(t)
            },
            resp => IO.now(Right(resp))
          )
        case Scan(req) =>
          eff(client.scan(JavaRequests.scan(req)))
        case Query(req) =>
          eff(client.query(JavaRequests.query(req)))
        case BatchWrite(req) =>
          eff(client.batchWriteItem(req))
        case BatchGet(req) =>
          eff(client.batchGetItem(req))
        case Update(req) =>
          eff(client.updateItem(JavaRequests.update(req)))
        case ConditionalUpdate(req) =>
          eff(client.updateItem(JavaRequests.update(req))).redeem(
            {
              case e : ConditionalCheckFailedException => IO.now(Left(e))
              case t                                  => IO.fail(t)
            },
            resp => IO.now(Right(resp))
          )
      }
    }


  implicit class IOObjOps(private val ioObj: IO.type) extends AnyVal {
    private def unsafeFromFuture[A](ec: ExecutionContext, f: Future[A]): IO[Throwable, A] =
      f.value.fold{
        IO.async[Throwable, A] {
          cb  => {
            f.onComplete {
              case Success(value) =>
                cb(ExitResult.succeeded(value))
              case Failure(value: CompletionException) =>
                cb(ExitResult.failed(ExitResult.Cause.checked(value.getCause)))
              case Failure(value) =>
                cb(ExitResult.failed(ExitResult.Cause.checked(value)))
            }(ec)
          }
        }
      }(IO.fromTry(_))

    def fromFuture[A](ec: ExecutionContext)(ftr: () => Future[A]): IO[Throwable, A] =
      IO.suspend {
        unsafeFromFuture(ec, ftr())
      }

    def fromFutureIO[A, E >: Throwable](ec: ExecutionContext)(ftrio: IO[E, Future[A]]): IO[E, A] =
      ftrio.flatMap(unsafeFromFuture(ec, _))
  }
}
