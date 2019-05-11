package org.scanamo.ops.retrypolicy

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import com.amazonaws.services.dynamodbv2.model._
import scala.concurrent._
import scala.concurrent.duration._

object RetryUtility {

  def retryWithBackOff[T](op: => Future[T],
                          retryPolicy: Linear)(implicit executionContext: ExecutionContext): Future[T] =
    op.recoverWith {
      case exception @ (_: InternalServerErrorException | _: ItemCollectionSizeLimitExceededException |
          _: LimitExceededException | _: ProvisionedThroughputExceededException | _: RequestLimitExceededException) =>
        val retries = retryPolicy.retries
        val scheduler = retryPolicy.scheduler
        val initialDelay = retryPolicy.initialDelay.toMillis
        val factor = retryPolicy.factor
        if (retries > 0) {
          for {
            _ <- waitForMillis(initialDelay, scheduler)
            newRetrySetting = Linear((initialDelay * factor).millis, factor, retries - 1, scheduler)
            response <- retryWithBackOff(op, newRetrySetting)
          } yield response
        } else {
          Future.failed(exception)
        }
    }

  private def waitForMillis(millis: Long, scheduler: ScheduledExecutorService) = {
    val promise = Promise[Long]
    scheduler.schedule(new Runnable {
      override def run() = {
        promise.success(millis)
        ()
      }
    }, millis, TimeUnit.MILLISECONDS)
    promise.future
  }
}
