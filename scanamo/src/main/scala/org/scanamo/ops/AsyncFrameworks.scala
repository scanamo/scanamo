package org.scanamo.ops

import cats.~>
import org.scanamo.ops.ScanamoOps.Results.*
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{ ConditionalCheckFailedException, TransactionCanceledException }

import java.util.concurrent.{ CompletableFuture, CompletionException }

object AsyncFrameworks {
  private[ops] val unwrapCompletionException: PartialFunction[Throwable, Throwable] = { case e: CompletionException =>
    e.getCause
  }

  /** Adapter for the framework-specific actions needed for dealing with Scanamo/DynamoDbAsyncClient.
    * DynamoDbAsyncClient uses java.util.concurrent.CompletableFuture, so there needs to be a way of converting that to
    * the framework's own asynchronous type (eg Future, IO, etc). We also need to be able to extract specific exceptions
    * from that type.
    */
  trait Adapter[F[_]] {
    def run[Out](fut: => CompletableFuture[Out]): F[Out]

    def exposeException[Out, E <: Exception](o: F[Out])(rF: PartialFunction[Throwable, E]): F[Either[E, Out]]
  }

  /** Helper for creating Scanamo interpreters for async frameworks like Future, Cats Effect, ZIO, all of which can use
    * DynamoDbAsyncClient and translate java.util.concurrent.CompletableFuture into their own types (eg IO, Future, etc)
    * for representing asynchronous processes.
    */
  class Interpreter[F[_]](client: DynamoDbAsyncClient, frameworkAdapter: Adapter[F]) extends (ScanamoOpsA ~> F) {

    import frameworkAdapter.*

    def runConditional[Out](fut: => CompletableFuture[Out]): F[Conditional[Out]] =
      exposeException(run(fut)) { case e: ConditionalCheckFailedException => e }

    def runTransact[Out](fut: => CompletableFuture[Out]): F[Transact[Out]] =
      exposeException(run(fut)) { case e: TransactionCanceledException => e }

    def apply[A](ops: ScanamoOpsA[A]): F[A] = ops match {
      case Put(req)               => run(client.putItem(JavaRequests.put(req)))
      case ConditionalPut(req)    => runConditional(client.putItem(JavaRequests.put(req)))
      case Get(req)               => run(client.getItem(req))
      case Delete(req)            => run(client.deleteItem(JavaRequests.delete(req)))
      case ConditionalDelete(req) => runConditional(client.deleteItem(JavaRequests.delete(req)))
      case Scan(req)              => run(client.scan(JavaRequests.scan(req)))
      case Query(req)             => run(client.query(JavaRequests.query(req)))
      case BatchWrite(req)        => run(client.batchWriteItem(req))
      case BatchGet(req)          => run(client.batchGetItem(req))
      case Update(req)            => run(client.updateItem(JavaRequests.update(req)))
      case ConditionalUpdate(req) => runConditional(client.updateItem(JavaRequests.update(req)))
      case TransactWriteAll(req)  => runTransact(client.transactWriteItems(JavaRequests.transactItems(req)))
    }
  }
}
