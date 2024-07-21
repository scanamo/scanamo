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

import cats.Monad
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.stream.scaladsl.{ Sink, Source }
import org.scanamo.PekkoInstances.*
import org.scanamo.ops.{ PekkoInterpreter, ScanamoOps }
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.concurrent.Future

/** `ScanamoPekko` is a `ScanamoClient` that uses Pekko (the Akka alternative), which returns either
  * [[scala.concurrent.Future]] or [[org.apache.pekko.stream.scaladsl.Source]] based on the kind of execution used.
  *
  * This is a port of [[https://github.com/scanamo/scanamo/pull/151 ScanamoAlpakka]], which has since been removed from
  * the core Scanamo project.
  */
class ScanamoPekko private (client: DynamoDbAsyncClient)(implicit system: ClassicActorSystemProvider)
    extends ScanamoClient(new PekkoInterpreter()(client, system)) {

  def execFuture[A](op: ScanamoOps[A]): Future[A] = exec(op).runWith(Sink.head[A])
}

object ScanamoPekko {
  type Pekko[A] = Source[A, NotUsed]

  def apply(client: DynamoDbAsyncClient)(implicit system: ClassicActorSystemProvider): ScanamoPekko =
    new ScanamoPekko(client)
}

private[scanamo] object PekkoInstances {
  implicit val monad: Monad[Source[*, NotUsed]] = new Monad[Source[*, NotUsed]] {
    def pure[A](x: A): Source[A, NotUsed] = Source.single(x)

    def flatMap[A, B](fa: Source[A, NotUsed])(f: A => Source[B, NotUsed]): Source[B, NotUsed] = fa.flatMapConcat(f)

    def tailRecM[A, B](a: A)(f: A => Source[Either[A, B], NotUsed]): Source[B, NotUsed] =
      f(a).flatMapConcat {
        case Left(a)  => tailRecM(a)(f)
        case Right(b) => Source.single(b)
      }
  }
}
