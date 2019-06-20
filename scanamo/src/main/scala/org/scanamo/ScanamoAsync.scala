package org.scanamo

import cats.Monad
import cats.~>
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo.ops._
import scala.concurrent.{ ExecutionContext, Future }

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an implicit
  * concurrent.ExecutionContext and returns a concurrent.Future
  *
  * Note that that com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient just uses an
  * java.util.concurrent.ExecutorService to make calls asynchronously
  */
class ScanamoAsync private (client: AmazonDynamoDBAsync)(implicit ec: ExecutionContext) {
  import cats.instances.future._

  final private val interpreter = new ScanamoAsyncInterpreter(client)

  /**
    * Execute the operations built with [[org.scanamo.Table]], using the client
    * provided asynchronously
    */
  final def exec[A](op: ScanamoOps[A]): Future[A] = op.foldMap(interpreter)

  final def execT[M[_]: Monad, A](hoist: Future ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object ScanamoAsync {
  def apply(client: AmazonDynamoDBAsync)(implicit ec: ExecutionContext): ScanamoAsync = new ScanamoAsync(client)
}
