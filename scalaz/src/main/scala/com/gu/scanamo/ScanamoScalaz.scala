package org.scanamo

import org.scanamo.ops.{ScalazInterpreter, ScanamoOps}
import scalaz.ioeffect.Task
import shims._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.concurrent.ExecutionContext

object ScanamoScalaz {

  def exec[A](client: DynamoDbAsyncClient)(op: ScanamoOps[A])(implicit ec: ExecutionContext): Task[A] =
    op.asScalaz.foldMap(ScalazInterpreter.io(client))

}
