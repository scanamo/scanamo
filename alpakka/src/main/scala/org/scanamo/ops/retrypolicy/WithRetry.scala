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

import java.util.concurrent.{ TimeUnit }

import com.amazonaws.services.dynamodbv2.model._

import akka.stream.scaladsl.Source
import akka.NotUsed
import scala.concurrent.duration.FiniteDuration

trait WithRetry {
  final def retry[T](op: => Source[T, NotUsed], retryPolicy: RetryPolicy): Source[T, NotUsed] =
    op.recoverWithRetries(
      1, {
        case exception @ (_: InternalServerErrorException | _: ItemCollectionSizeLimitExceededException |
            _: LimitExceededException | _: ProvisionedThroughputExceededException | _: RequestLimitExceededException) =>
          if (retryPolicy.continue) {
            Source
              .single(())
              .delay(FiniteDuration(retryPolicy.delay.toMillis, TimeUnit.MILLISECONDS))
              .flatMapConcat(_ => retry(op, retryPolicy.update))
          } else {
            Source.failed(exception)
          }
      }
    )
}
