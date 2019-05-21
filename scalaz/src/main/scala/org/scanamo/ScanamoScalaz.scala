package org.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo.ops.{ScalazInterpreter, ScanamoOps}
import scalaz.ioeffect.Task
import shims._

class ScanamoScalaz(client: AmazonDynamoDBAsync) {

  private final val interpreter = new ScalazInterpreter(client)

  final def exec[A](op: ScanamoOps[A]): Task[A] = op.asScalaz.foldMap(interpreter)

}

object ScanamoScalaz {
  def apply(client: AmazonDynamoDBAsync): ScanamoScalaz = new ScanamoScalaz(client)
}
