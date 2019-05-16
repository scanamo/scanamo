package org.scanamo.ops.retrypolicy

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

import com.amazonaws.services.dynamodbv2.model._

import scala.concurrent._

object RetryUtility {

  def retryWithBackOff[T](op: => Future[T], retryPolicy: RetryPolicy)(
    implicit executionContext: ExecutionContext,
    scheduler: ScheduledExecutorService
  ): Future[T] =
    op.recoverWith {
      case exception @ (_: InternalServerErrorException | _: ItemCollectionSizeLimitExceededException |
          _: LimitExceededException | _: ProvisionedThroughputExceededException | _: RequestLimitExceededException) =>
        val delay = retryPolicy.delay

        if (!retryPolicy.done) {
          for {
            _ <- waitForMillis(delay, scheduler)
            response <- retryWithBackOff(op, retryPolicy.update)
          } yield response
        } else {
          Future.failed(exception)
        }
    }

  private def waitForMillis(millis: Long, scheduler: ScheduledExecutorService) = {
    val promise = Promise[Long]

    scheduler.schedule(new Runnable {
      override def run(): Unit = {
        promise.success(millis)
        ()
      }
    }, millis, TimeUnit.MILLISECONDS)

    promise.future
  }
}
