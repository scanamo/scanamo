package org.scanamo

import org.scanamo.ops.{ScanamoOps, ZioInterpreter}
import scalaz.zio.IO
import scalaz.zio.interop.catz._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException

object ScanamoZio {

  def exec[A](client: DynamoDbAsyncClient)(op: ScanamoOps[A]): IO[DynamoDbException, A] =
    op.foldMap(ZioInterpreter.effect(client))

}
