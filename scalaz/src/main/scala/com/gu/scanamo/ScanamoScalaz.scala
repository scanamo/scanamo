package org.scanamo

import org.scanamo.ops.{ScalazInterpreter, ScanamoOps}
import scalaz.ioeffect.Task
import shims._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

object ScanamoScalaz {

  def exec[A](client: DynamoDbAsyncClient)(op: ScanamoOps[A]): Task[A] =
    op.asScalaz.foldMap(ScalazInterpreter.io(client))

}
