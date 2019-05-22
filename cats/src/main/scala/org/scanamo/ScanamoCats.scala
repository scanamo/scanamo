package org.scanamo

import cats.effect.Async
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo.ops.{ CatsInterpreter, ScanamoOps }

class ScanamoCats[F[_]: Async](client: AmazonDynamoDBAsync) {

  final private val interpreter = new CatsInterpreter(client)

  final def exec[A](op: ScanamoOps[A]): F[A] = op.foldMap(interpreter)

}

object ScanamoCats {
  def apply[F[_]: Async](client: AmazonDynamoDBAsync): ScanamoCats[F] = new ScanamoCats(client)
}
