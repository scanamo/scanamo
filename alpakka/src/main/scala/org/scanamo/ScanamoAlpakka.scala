package org.scanamo

import akka.stream.alpakka.dynamodb.AwsOp
import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import org.scanamo.ops.retrypolicy.RetryPolicy
import org.scanamo.ops.{ AlpakkaInterpreter, ScanamoOps }

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client
  * and an implicit [[scala.concurrent.ExecutionContext]] and returns a [[scala.concurrent.Future]]
  */
object ScanamoAlpakka {
  import cats.instances.future._

  def exec[A](client: DynamoClient)(op: ScanamoOps[A])(implicit ec: ExecutionContext, retrySettings : RetryPolicy): Future[A] =
    op.foldMap(AlpakkaInterpreter.future(client, retrySettings)(ec))

  def singleRequest(client: DynamoClient, op: AwsOp)(implicit ec: ExecutionContext, retrySettings: RetryPolicy) : Future[AwsOp#B] =
    AlpakkaInterpreter.executeSingleRequest(client, op, retrySettings)(ec)
}
