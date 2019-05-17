package org.scanamo.ops.retrypolicy

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

import com.amazonaws.services.dynamodbv2.model._

import scala.concurrent._

object RetryUtility {

  def retry[T](op: => Future[T], retryPolicy: RetryPolicy)(implicit ec: ExecutionContext): Future[T] =
    op.recoverWith {
      case exception @ (_: InternalServerErrorException | _: ItemCollectionSizeLimitExceededException |
          _: LimitExceededException | _: ProvisionedThroughputExceededException | _: RequestLimitExceededException) =>
        val delay = retryPolicy.delay

        if (!retryPolicy.done) {
          for {
            _ <- waitForMillis(delay, retryPolicy.maybeScheduler)
            response <- retry(op, retryPolicy.update)
          } yield response
        } else {
          Future.failed(exception)
        }
    }

  private def waitForMillis(millis: Long, maybeScheduler: Option[ScheduledExecutorService]) = {
    val promise = Promise[Long]

    maybeScheduler.fold {
      promise.success(millis)
      ()
    } { scheduler =>
      scheduler.schedule(new Runnable {
        override def run(): Unit = {
          promise.success(millis)
          ()
        }
      }, millis, TimeUnit.MILLISECONDS)
      ()
    }

    promise.future
  }
}
