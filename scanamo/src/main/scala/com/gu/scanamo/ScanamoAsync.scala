package org.scanamo

import org.scanamo.ops.{ScanamoInterpreters, ScanamoOps}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an implicit
  * concurrent.ExecutionContext and returns a concurrent.Future
  *
  * Note that that com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient just uses an
  * java.util.concurrent.ExecutorService to make calls asynchronously
  */
object ScanamoAsync {
  import cats.instances.future._

  /**
    * Execute the operations built with [[org.scanamo.Table]], using the client
    * provided asynchronously
    */
  def exec[A](client: DynamoDbAsyncClient)(op: ScanamoOps[A])(implicit ec: ExecutionContext): Future[A] =
    op.foldMap(ScanamoInterpreters.future(client)(ec))

}
