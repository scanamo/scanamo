package org.scanamo

import java.util.concurrent.ScheduledExecutorService

import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import org.scanamo.ops.retrypolicy.RetryPolicy
import org.scanamo.ops.AlpakkaInterpreter
import org.scanamo.ops.ScanamoOps
import org.scanamo.ops.retrypolicy.DefaultRetryPolicy

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client
  * and an implicit [[scala.concurrent.ExecutionContext]] and returns a [[scala.concurrent.Future]]
  */
object ScanamoAlpakka {
  import cats.instances.future._

  def exec[A](
    client: DynamoClient
  )(op: ScanamoOps[A], retrySettings: RetryPolicy = DefaultRetryPolicy.defaultPolicy)(
    implicit ec: ExecutionContext,
    scheduler: ScheduledExecutorService = DefaultRetryPolicy.defaultScheduler
  ): Future[A] =
    op.foldMap(AlpakkaInterpreter.future(client, retrySettings)(ec, scheduler))
}
