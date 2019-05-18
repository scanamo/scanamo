package org.scanamo

import org.scanamo.ops.{ AlpakkaInterpreter, ScanamoOps }
import org.scanamo.ops.retrypolicy.RetryPolicy
import akka.stream.scaladsl.Source
import akka.NotUsed
import akka.stream.alpakka.dynamodb.DynamoClient
import cats.Monad

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client,
  * and an implicit [[scala.concurrent.ExecutionContext]] and returns a [[scala.concurrent.Future]]
  */
object ScanamoAlpakka extends AlpakkaInstances {
  def exec[A](
    client: DynamoClient
  )(
    op: ScanamoOps[A],
    retrySettings: RetryPolicy = RetryPolicy.Max(numberOfRetries = 3)
  ): Source[A, NotUsed] =
    op.foldMap(AlpakkaInterpreter.future(client, retrySettings))
}

private[scanamo] trait AlpakkaInstances {
  implicit def monad: Monad[Source[?, NotUsed]] =
    new Monad[Source[?, NotUsed]] {
      def pure[A](x: A): Source[A, NotUsed] = Source.single(x)

      def flatMap[A, B](fa: Source[A, NotUsed])(
        f: A => Source[B, NotUsed]
      ): Source[B, NotUsed] = fa.flatMapConcat(f)

      def tailRecM[A, B](a: A)(
        f: A => Source[Either[A, B], NotUsed]
      ): Source[B, NotUsed] = f(a).flatMapConcat {
        case Left(a) => tailRecM(a)(f)
        case Right(b) => Source.single(b)
      }
    }
}
