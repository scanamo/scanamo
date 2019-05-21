package org.scanamo

import akka.NotUsed
import akka.stream.alpakka.dynamodb.DynamoClient
import akka.stream.scaladsl.Source
import cats.Monad
import org.scanamo.ops.{AlpakkaInterpreter, ScanamoOps}
import org.scanamo.ops.retrypolicy.RetryPolicy

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client,
  * and an implicit [[scala.concurrent.ExecutionContext]] and returns a [[scala.concurrent.Future]]
  */
class ScanamoAlpakka private (client: DynamoClient, retrySettings: RetryPolicy) {
  import ScanamoAlpakka._
  
  private final val interpreter = new AlpakkaInterpreter(client, retrySettings)

  def exec[A](op: ScanamoOps[A]): AlpakkaInterpreter.Alpakka[A] =
    op.foldMap(interpreter)
}

object ScanamoAlpakka extends AlpakkaInstances {
  def apply(
    client: DynamoClient,
    retrySettings: RetryPolicy = RetryPolicy.Max(numberOfRetries = 3)
  ): ScanamoAlpakka = new ScanamoAlpakka(client, retrySettings)
}

private[scanamo] trait AlpakkaInstances {
  implicit def monad: Monad[Source[?, NotUsed]] = new Monad[Source[?, NotUsed]] {
    def pure[A](x: A): Source[A, NotUsed] = Source.single(x)

    def flatMap[A, B](fa: Source[A, NotUsed])(f: A => Source[B, NotUsed]): Source[B, NotUsed] = fa.flatMapConcat(f)

    def tailRecM[A, B](a: A)(f: A => Source[Either[A, B], NotUsed]): Source[B, NotUsed] = f(a).flatMapConcat {
      case Left(a)  => tailRecM(a)(f)
      case Right(b) => Source.single(b)
    }
  }
}