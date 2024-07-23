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

import cats.effect.Async
import cats.~>
import fs2.Stream
import org.scanamo.ops.{ AsyncFrameworks, CatsAdapter }
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

class ScanamoCats[F[_]: Async] private (client: DynamoDbAsyncClient)
    extends ScanamoClient(new AsyncFrameworks.Interpreter(client, new CatsAdapter()))

object ScanamoCats {
  def apply[F[_]: Async](client: DynamoDbAsyncClient): ScanamoCats[F] = new ScanamoCats(client)

  def ToStream[F[_]: Async]: F ~> Stream[F, _] =
    new (F ~> Stream[F, _]) {
      def apply[A](fa: F[A]): Stream[F, A] = Stream.eval(fa)
    }
}
