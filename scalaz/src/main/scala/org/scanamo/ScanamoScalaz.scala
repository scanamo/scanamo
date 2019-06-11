package org.scanamo

import cats.arrow.FunctionK
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo.ops.{ ScalazInterpreter, ScanamoOps, ScanamoOpsT }
import scalaz.{ ~>, Monad }
import scalaz.ioeffect.Task
import shims._

class ScanamoScalaz(client: AmazonDynamoDBAsync) {
  import ScanamoScalaz._

  final private val interpreter = new ScalazInterpreter(client)

  final def exec[A](op: ScanamoOps[A]): Task[A] = op.asScalaz.foldMap(interpreter)

  final def execT[M[_]: Monad, A](hoist: Task ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object ScanamoScalaz {
  implicit def natToNat[F[_], G[_]](f: F ~> G): FunctionK[F, G] = new FunctionK[F, G] {
    def apply[A](a: F[A]): G[A] = f(a)
  }

  def apply(client: AmazonDynamoDBAsync): ScanamoScalaz = new ScanamoScalaz(client)
}
