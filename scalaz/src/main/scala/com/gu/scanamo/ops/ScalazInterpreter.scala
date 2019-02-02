package org.scanamo.ops

import java.util.concurrent.CompletionException

import scalaz.ioeffect.{ExitResult, IO, Task}
import scalaz.~>
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException

import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ScalazInterpreter {
  def io(client: DynamoDbAsyncClient)(implicit ec: ExecutionContext) = new (ScanamoOpsA ~> Task) {
    private[this] def eff[B]
    (
      f: java.util.concurrent.CompletionStage[B]
    ): Task[B] =
      IO.async { cb =>
        FutureConverters.toScala(f).onComplete {
          case Success(v) => cb(ExitResult.Completed(v))
          case Failure(e: CompletionException) ⇒ cb(ExitResult.Failed(e.getCause))
          case Failure(e) ⇒ cb(ExitResult.Failed(e))
        }
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
          eff(client.putItem(JavaRequests.put(req)))
        case ConditionalPut(req) =>
          catchConditional(eff(client.putItem(JavaRequests.put(req))))
        case Get(req) =>
          eff(client.getItem(req))
        case Delete(req) =>
          eff(client.deleteItem(JavaRequests.delete(req)))
        case ConditionalDelete(req) =>
          catchConditional(eff(client.deleteItem(JavaRequests.delete(req))))
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
          catchConditional(eff(client.updateItem(JavaRequests.update(req))))
      }
  }
}
