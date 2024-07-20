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

import org.scanamo.ScanamoZio.DIO
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import zio.{ IO, ZIO }

import java.util.concurrent.CompletableFuture

object ZioSpecific extends AsyncFrameworks.FrameworkSpecific[DIO] {
  def run[Out](fut: => CompletableFuture[Out]): DIO[Out] =
    ZIO.fromCompletionStage(fut).refineToOrDie[DynamoDbException]

  def exposeException[Out, E <: Exception](value: DIO[Out])(rF: PartialFunction[Throwable, E]): DIO[Either[E, Out]] =
    value.map(Right(_)).catchSome(rF.andThen(f => IO.succeed(Left(f))))
}
