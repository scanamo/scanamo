package org.scanamo

import cats.effect.Async
import org.scanamo.ops.{CatsInterpreter, ScanamoOps}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.concurrent.ExecutionContext

object ScanamoCats {

  def exec[F[_]: Async, A](client: DynamoDbAsyncClient)(op: ScanamoOps[A])(implicit ec: ExecutionContext): F[A] =
    op.foldMap(CatsInterpreter.effect(client))

}
