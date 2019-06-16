package org.scanamo

import cats.{ ~>, Alternative, Monad }
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import org.scanamo.ops._
import zio.IO
import zio.interop.catz._
import zio.stream.{ Stream, ZStream }

class ScanamoZio private (client: AmazonDynamoDBAsync) {
  final private val interpreter: ScanamoOpsA ~> IO[AmazonDynamoDBException, ?] =
    new ZioInterpreter(client)

  final def exec[A](op: ScanamoOps[A]): IO[AmazonDynamoDBException, A] = op.foldMap(interpreter)

  final def execT[M[_]: Monad, A](hoist: IO[AmazonDynamoDBException, ?] ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object ScanamoZio {
  implicit def streamInstances[E] = new Monad[Stream[E, ?]] with Alternative[Stream[E, ?]] {
    def combineK[A](x: Stream[E, A], y: Stream[E, A]): Stream[E, A] =
      x ++ y

    def empty[A]: Stream[E, A] = Stream.empty

    def flatMap[A, B](fa: Stream[E, A])(f: A => Stream[E, B]): Stream[E, B] = fa flatMap f

    def pure[A](x: A): Stream[E, A] = Stream.succeed(x)

    def tailRecM[A, B](a: A)(f: A => Stream[E, Either[A, B]]): Stream[E, B] = f(a) flatMap {
      case Left(a)  => tailRecM(a)(f)
      case Right(b) => Stream.succeed(b)
    }
  }

  def apply(client: AmazonDynamoDBAsync): ScanamoZio = new ScanamoZio(client)

  val IoToStream: IO[AmazonDynamoDBException, ?] ~> Stream[AmazonDynamoDBException, ?] =
    new (IO[AmazonDynamoDBException, ?] ~> Stream[AmazonDynamoDBException, ?]) {
      def apply[A](fa: IO[AmazonDynamoDBException, A]): Stream[AmazonDynamoDBException, A] = ZStream.fromEffect(fa)
    }
}
