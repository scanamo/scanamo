package org.scanamo

import cats.{ ~>, Monad }
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo.ops.{ ScalazInterpreter, ScanamoOps }
import scalaz.ioeffect.Task
import shims._

class ScanamoScalaz(client: AmazonDynamoDBAsync) {

  final private val interpreter = new ScalazInterpreter(client)

  final def exec[A](op: ScanamoOps[A]): Task[A] = op.asScalaz.foldMap(interpreter)

  final def execT[M[_]: Monad, A](hoist: Task ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object ScanamoScalaz {
  def apply(client: AmazonDynamoDBAsync): ScanamoScalaz = new ScanamoScalaz(client)
}
