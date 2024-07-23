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

import java.util.concurrent.{ CompletableFuture, CompletionException }
import scala.compat.java8.FutureConverters.*
import scala.concurrent.{ ExecutionContext, Future }

class ScalaFutureAdapter(implicit ec: ExecutionContext) extends AsyncFrameworks.Adapter[Future] {

  def run[Out](fut: => CompletableFuture[Out]): Future[Out] =
    fut.toScala.recoverWith { case error: CompletionException => Future.failed(error.getCause) }

  def exposeException[Out, E <: Exception](value: Future[Out])(
    rF: PartialFunction[Throwable, E]
  ): Future[Either[E, Out]] = value.map(Right[E, Out]).recover(rF.andThen(Left(_)))
}
