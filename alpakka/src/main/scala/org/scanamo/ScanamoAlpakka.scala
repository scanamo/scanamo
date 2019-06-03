package org.scanamo

import akka.NotUsed
import akka.stream.alpakka.dynamodb.DynamoClient
import akka.stream.scaladsl.{ Sink, Source }
import cats.Monad
import org.scanamo.ops.AlpakkaInterpreter.Alpakka
import org.scanamo.ops.{ AlpakkaInterpreter, ScanamoOps }
import org.scanamo.ops.retrypolicy.RetryPolicy

import scala.concurrent.Future

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client and a [[org.scanamo.ops.retrypolicy.RetryPolicy]].
  * `retryPolicy` defaults to [[org.scanamo.ops.retrypolicy.RetryPolicy.Max]] with maximum 3 retries if not explicitly
  * provided. Moreover, the interface returns either a [[scala.concurrent.Future]] or [[akka.stream.scaladsl.Source]]
  * based on the kind of execution used.
  */
class ScanamoAlpakka private (client: DynamoClient, retryPolicy: RetryPolicy) {
  import ScanamoAlpakka._

  final private val interpreter = new AlpakkaInterpreter(client, retryPolicy)

  def exec[A](op: ScanamoOps[A]): Alpakka[A] =
    run(op)

  def execFuture[A](op: ScanamoOps[A]): Future[A] =
    run(op).runWith(Sink.head[A])(client.materializer)

  private def run[A](op: ScanamoOps[A]): Alpakka[A] =
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
