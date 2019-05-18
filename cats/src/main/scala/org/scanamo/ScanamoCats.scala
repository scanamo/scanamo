package org.scanamo

import cats.effect.Async
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo.ops.{CatsInterpreter, ScanamoOps}

class ScanamoCats[F[_]: Async](client: AmazonDynamoDBAsync) {

  private final val interpreter = new CatsInterpreter(client)

  def exec[A](op: ScanamoOps[A]): F[A] = op.foldMap(interpreter)

}
