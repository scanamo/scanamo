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
import cats.effect.Async
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import fs2.Stream
import monix.tail.Iterant
import org.scanamo.ops.{ CatsInterpreter, ScanamoOps, ScanamoOpsT }

class ScanamoCats[F[_]: Async](client: DynamoDbAsyncClient) {
  final private val interpreter = new CatsInterpreter(client)

  final def exec[A](op: ScanamoOps[A]): F[A] = op.foldMap(interpreter)

  final def execT[M[_]: Monad, A](hoist: F ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object ScanamoCats {
  def apply[F[_]: Async](client: DynamoDbAsyncClient): ScanamoCats[F] = new ScanamoCats(client)

  def ToIterant[F[_]: Async]: F ~> Iterant[F, ?] =
    new (F ~> Iterant[F, ?]) {
      def apply[A](fa: F[A]): Iterant[F, A] = Iterant.liftF(fa)
    }

  def ToStream[F[_]: Async]: F ~> Stream[F, ?] =
    new (F ~> Stream[F, ?]) {
      def apply[A](fa: F[A]): Stream[F, A] = Stream.eval(fa)
    }
}
