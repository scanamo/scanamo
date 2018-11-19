package com.gu.scanamo

import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import com.gu.scanamo.ops.{AlpakkaInterpreter, ScanamoOps}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides the same interface as [[com.gu.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client
  * and an implicit [[scala.concurrent.ExecutionContext]] and returns a [[scala.concurrent.Future]]
  */
object ScanamoAlpakka {
  import cats.instances.future._

  def exec[A](client: DynamoClient)(op: ScanamoOps[A])(implicit ec: ExecutionContext): Future[A] =
    op.foldMap(AlpakkaInterpreter.future(client)(ec))

}
