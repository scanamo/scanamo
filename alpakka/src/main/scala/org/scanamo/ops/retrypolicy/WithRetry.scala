package org.scanamo.ops.retrypolicy

import java.util.concurrent.{TimeUnit}

import com.amazonaws.services.dynamodbv2.model._

import akka.stream.scaladsl.Source
import akka.NotUsed
import scala.concurrent.duration.FiniteDuration

trait WithRetry {

  final def retry[T](op: => Source[T, NotUsed], retryPolicy: RetryPolicy): Source[T, NotUsed] =
    op.recoverWithRetries(
      retryPolicy.retries, {
        case exception @ (_: InternalServerErrorException | _: ItemCollectionSizeLimitExceededException |
            _: LimitExceededException | _: ProvisionedThroughputExceededException | _: RequestLimitExceededException) =>
          if (!retryPolicy.done) {
            retry(op, retryPolicy.update).delay(FiniteDuration(retryPolicy.delay, TimeUnit.MILLISECONDS))
          } else {
            Source.failed(exception)
          }
      }
    )
}
