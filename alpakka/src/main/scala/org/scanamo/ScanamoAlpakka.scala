/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo

import cats.{ ~>, Monad }
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  DynamoDbException,
  InternalServerErrorException,
  ItemCollectionSizeLimitExceededException,
  LimitExceededException,
  ProvisionedThroughputExceededException,
  RequestLimitExceededException
}
import org.scanamo.ops.AlpakkaInterpreter.Alpakka
import org.scanamo.ops.{ AlpakkaInterpreter, ScanamoOps, ScanamoOpsT }
import org.scanamo.ops.retrypolicy.RetryPolicy

import scala.concurrent.Future

/**
  * Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/akka/alpakka Alpakka]] client, a [[org.scanamo.ops.retrypolicy.RetryPolicy]]
  * and a predicate for which [[scala.Throwable]]s should be retried.
  * `retryPolicy` defaults to [[org.scanamo.ops.retrypolicy.RetryPolicy.max]] with maximum 3 retries if not explicitly
  * provided. `isRetryable` defaults to retry the most common retryable Dynamo exceptions.
  * Moreover, the interface returns either a [[scala.concurrent.Future]] or [[akka.stream.scaladsl.Source]]
  * based on the kind of execution used.
  */
class ScanamoAlpakka private (client: DynamoDbAsyncClient, retryPolicy: RetryPolicy, isRetryable: Throwable => Boolean)(
  implicit mat: Materializer
) {
  import ScanamoAlpakka._

  final private val interpreter = new AlpakkaInterpreter(retryPolicy, isRetryable)(client, mat)

  def exec[A](op: ScanamoOps[A]): Alpakka[A] =
    run(op)

  final def execT[M[_]: Monad, A](hoist: Alpakka ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)

  def execFuture[A](op: ScanamoOps[A]): Future[A] =
    run(op).runWith(Sink.head[A])

  private def run[A](op: ScanamoOps[A]): Alpakka[A] =
    op.foldMap(interpreter)
}

object ScanamoAlpakka extends AlpakkaInstances {
  def apply(
    client: DynamoDbAsyncClient,
    retrySettings: RetryPolicy = RetryPolicy.max(3),
    isRetryable: Throwable => Boolean = defaultRetryableCheck
  )(implicit mat: Materializer): ScanamoAlpakka = new ScanamoAlpakka(client, retrySettings, isRetryable)

  def defaultRetryableCheck(throwable: Throwable): Boolean =
    throwable match {
      case _: InternalServerErrorException | _: ItemCollectionSizeLimitExceededException | _: LimitExceededException |
          _: ProvisionedThroughputExceededException | _: RequestLimitExceededException =>
        true
      case e: DynamoDbException
          if e.awsErrorDetails.errorCode.contains("ThrottlingException") |
            e.awsErrorDetails.errorCode.contains("InternalFailure") =>
        true
      case _ => false
    }
}

private[scanamo] trait AlpakkaInstances {
  implicit def monad: Monad[Source[*, NotUsed]] =
    new Monad[Source[*, NotUsed]] {
      def pure[A](x: A): Source[A, NotUsed] = Source.single(x)

      def flatMap[A, B](fa: Source[A, NotUsed])(f: A => Source[B, NotUsed]): Source[B, NotUsed] = fa.flatMapConcat(f)

      def tailRecM[A, B](a: A)(f: A => Source[Either[A, B], NotUsed]): Source[B, NotUsed] =
        f(a).flatMapConcat {
          case Left(a)  => tailRecM(a)(f)
          case Right(b) => Source.single(b)
        }
    }
}
