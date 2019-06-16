package org.scanamo

import cats.{ ~>, Monad }
import cats.effect.Async
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import fs2.Stream
import monix.tail.Iterant
import org.scanamo.ops.{ CatsInterpreter, ScanamoOps, ScanamoOpsT }

class ScanamoCats[F[_]: Async](client: AmazonDynamoDBAsync) {

  final private val interpreter = new CatsInterpreter(client)

  final def exec[A](op: ScanamoOps[A]): F[A] = op.foldMap(interpreter)

  final def execT[M[_]: Monad, A](hoist: F ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object ScanamoCats {
  def apply[F[_]: Async](client: AmazonDynamoDBAsync): ScanamoCats[F] = new ScanamoCats(client)

  def ToIterant[F[_]: Async]: F ~> Iterant[F, ?] =
    new (F ~> Iterant[F, ?]) {
      def apply[A](fa: F[A]): Iterant[F, A] = Iterant.liftF(fa)
    }

  def toStream[F[_]: Async]: F ~> Stream[F, ?] =
    new (F ~> Stream[F, ?]) {
      def apply[A](fa: F[A]): Stream[F, A] = Stream.eval(fa)
    }
}
