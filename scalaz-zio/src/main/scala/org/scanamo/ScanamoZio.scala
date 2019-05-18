package org.scanamo

import cats.~>
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import org.scanamo.ops._
import scalaz.zio.interop.catz._
import scalaz.zio.IO

class ScanamoZio private (client: AmazonDynamoDBAsync) {
  private final val interpreter: ScanamoOpsA ~> IO[AmazonDynamoDBException, ?] =
    new ZioInterpreter(client)

  final def exec[A](op: ScanamoOps[A]): IO[AmazonDynamoDBException, A] = op.foldMap(interpreter)
}

object ScanamoZio {
  def apply(client: AmazonDynamoDBAsync): ScanamoZio = new ScanamoZio(client)
}
