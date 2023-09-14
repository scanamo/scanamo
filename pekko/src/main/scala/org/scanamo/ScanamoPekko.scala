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

import cats.~>
import cats.Monad
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException
import software.amazon.awssdk.services.dynamodb.model.ItemCollectionSizeLimitExceededException
import software.amazon.awssdk.services.dynamodb.model.LimitExceededException
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException
import software.amazon.awssdk.services.dynamodb.model.RequestLimitExceededException
import org.scanamo.ops.PekkoInterpreter.Pekko
import org.scanamo.ops.PekkoInterpreter
import org.scanamo.ops.ScanamoOps
import org.scanamo.ops.ScanamoOpsT

import scala.concurrent.Future

/** Provides the same interface as [[org.scanamo.Scanamo]], except that it requires an
  * [[https://github.com/apache/incubator-pekko-connectors Pekko connectors]] client, a
  * [[org.scanamo.ops.retrypolicy.RetryPolicy]] and a predicate for which [[scala.Throwable]]s should be retried.
  * `retryPolicy` defaults to [[org.scanamo.ops.retrypolicy.RetryPolicy.max]] with maximum 3 retries if not explicitly
  * provided. `isRetryable` defaults to retry the most common retryable Dynamo exceptions. Moreover, the interface
  * returns either a [[scala.concurrent.Future]] or [[org.apache.pekko.stream.scaladsl.Source]] based on the kind of
  * execution used.
  *
  * This is a port of
  * [[https://github.com/scanamo/scanamo/blob/master/alpakka/src/main/scala/org/scanamo/ScanamoAlpakka.scala ScanamoAlpakka]]
  */
class ScanamoPekko private (client: DynamoDbAsyncClient)(implicit system: ClassicActorSystemProvider) {
  import ScanamoPekko._

  final private val interpreter = new PekkoInterpreter()(client, system)

  def exec[A](op: ScanamoOps[A]): Pekko[A] =
    run(op)

  final def execT[M[_]: Monad, A](hoist: Pekko ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter.andThen(hoist))

  def execFuture[A](op: ScanamoOps[A]): Future[A] =
    run(op).runWith(Sink.head[A])

  private def run[A](op: ScanamoOps[A]): Pekko[A] =
    op.foldMap(interpreter)
}

object ScanamoPekko extends PekkoInstances {
  def apply(client: DynamoDbAsyncClient)(implicit system: ClassicActorSystemProvider): ScanamoPekko =
    new ScanamoPekko(client)

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

private[scanamo] trait PekkoInstances {
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
