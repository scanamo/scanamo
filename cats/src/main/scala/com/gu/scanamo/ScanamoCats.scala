package com.gu.scanamo

import cats.effect.Async
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.gu.scanamo.ops.{CatsInterpreter, ScanamoOps}

object ScanamoCats {

  def exec[F[_]: Async, A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A]): F[A] =
    op.foldMap(CatsInterpreter.effect(client))

}
