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

package org.scanamo.ops

import cats.effect.Async
import cats.syntax.applicative.*
import cats.syntax.applicativeError.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.option.*
import org.scanamo.ops.AsyncPlatform.AsyncFrameworkInterpreter
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import java.util.concurrent.{CompletableFuture, CompletionException}

class CatsInterpreter[F[_]](val client: DynamoDbAsyncClient)(implicit F: Async[F]) extends AsyncFrameworkInterpreter[F] {

  val platformSpecific = new AsyncPlatform.PlatformSpecific[F] {
    def run[Out](fut: => CompletableFuture[Out]): F[Out] = F.async { cb =>
      lazy val materialised = fut
      materialised.handle[Unit] { (a, x) =>
        if (a == null)
          x match {
            case t: CompletionException => cb(Left(t.getCause))
            case t                      => cb(Left(t))
          }
        else
          cb(Right(a))
      }
      F.delay(materialised.cancel(false)).void.some.pure[F]
    }

    def exposeException[Out, E](value: F[Out])(rF: PartialFunction[Throwable, E]): F[Either[E, Out]] =
      value.attempt.flatMap(
        _.fold(
          e => rF.andThen(exposed => F.delay[Either[E, Out]](Left(exposed))).applyOrElse(e, F.raiseError),
          a => F.delay(Right(a))
        )
      )
  }
}
