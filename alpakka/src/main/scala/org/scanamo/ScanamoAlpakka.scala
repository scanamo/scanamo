package org.scanamo

import java.util.concurrent.ScheduledExecutorService

import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import org.scanamo.ops.retrypolicy.{DefaultRetryPolicy, RetryPolicy}
import org.scanamo.ops.{AlpakkaInterpreter, ScanamoOps}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client,
  * and an implicit [[scala.concurrent.ExecutionContext]] and returns a [[scala.concurrent.Future]]
  */
object ScanamoAlpakka {
  import cats.instances.future._

  // TODO: Remove to forcefully add the new thread pool even if their retry policy does not involve schedulers
  def exec[A](
    client: DynamoClient
  )(op: ScanamoOps[A], retrySettings: RetryPolicy = DefaultRetryPolicy.defaultPolicy)(
    implicit ec: ExecutionContext,
    scheduler: ScheduledExecutorService = DefaultRetryPolicy.defaultScheduler
  ): Future[A] =
    op.foldMap(AlpakkaInterpreter.future(client, retrySettings)(ec, scheduler))
}
