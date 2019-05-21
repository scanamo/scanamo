package org.scanamo

import org.scanamo.ops.retrypolicy.RetryPolicy
import org.scanamo.ops.{AlpakkaInterpreter, ScanamoOps}

import scala.concurrent.{ExecutionContext, Future}
import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client,
  * and an implicit [[scala.concurrent.ExecutionContext]] and returns a [[scala.concurrent.Future]]
  */
class ScanamoAlpakka private (client: DynamoClient, retrySettings: RetryPolicy)(implicit ec: ExecutionContext) {
  import cats.instances.future._

  private final val interpreter = new AlpakkaInterpreter(client, retrySettings)

  def exec[A](op: ScanamoOps[A]): Future[A] =
    op.foldMap(interpreter)
}

object ScanamoAlpakka {
  def apply(
    client: DynamoClient,
    retrySettings: RetryPolicy = RetryPolicy.Max(numberOfRetries = 3)
  )(implicit ec: ExecutionContext): ScanamoAlpakka = new ScanamoAlpakka(client, retrySettings)
}
