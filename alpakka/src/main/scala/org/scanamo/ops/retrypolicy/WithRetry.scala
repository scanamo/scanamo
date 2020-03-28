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

package org.scanamo.ops.retrypolicy

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.stream.scaladsl.Source

import scala.concurrent.duration.FiniteDuration

trait WithRetry {
  def retryable(throwable: Throwable): Boolean

  final def retry[T](op: => Source[T, NotUsed], retryPolicy: RetryPolicy): Source[T, NotUsed] =
    op.recoverWithRetries(
      1, {
        case exception if retryable(exception) =>
          if (retryPolicy.continue) {
            Source
              .single(())
              .delay(FiniteDuration(retryPolicy.delayOrElse(Long.MaxValue)(_.toMillis), TimeUnit.MILLISECONDS))
              .flatMapConcat(_ => retry(op, retryPolicy.update))
          } else {
            Source.failed(exception)
          }
      }
    )
}
