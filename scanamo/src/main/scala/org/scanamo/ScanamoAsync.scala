package org.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo.ops._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an implicit
  * concurrent.ExecutionContext and returns a concurrent.Future
  *
  * Note that that com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient just uses an
  * java.util.concurrent.ExecutorService to make calls asynchronously
  */
class ScanamoAsync(client: AmazonDynamoDBAsync)(implicit ec: ExecutionContext) {
  import cats.instances.future._

  private final val interpreter = new ScanamoAsyncInterpreter(client)

  /**
    * Execute the operations built with [[org.scanamo.Table]], using the client
    * provided asynchronously
    */
  def exec[A](op: ScanamoOps[A]): Future[A] =
    op.foldMap(interpreter)

}
