package org.scanamo

import java.util.concurrent._

import com.amazonaws.services.dynamodbv2.model._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, _}

object RetryUtility {

  private final val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

  def retryWithBackOff[T](op: => Future[T],
                          retrySettings: RetrySettings)(implicit executionContext: ExecutionContext): Future[T] =
    op.recoverWith {
      case exception @ (_: InternalServerErrorException | _: ItemCollectionSizeLimitExceededException |
          _: LimitExceededException | _: ProvisionedThroughputExceededException | _: RequestLimitExceededException) =>
        val retries = retrySettings.retries
        val initialDelay = retrySettings.initialDelay.toMillis
        val factor = retrySettings.factor
        if (retries > 0) {
          for {
            _ <- waitForMillis(initialDelay)
            newRetrySetting = RetrySettings((initialDelay * factor).millis, factor, retries - 1)
            response <- retryWithBackOff(op, newRetrySetting)
          } yield response
        } else {
          Future.failed(exception)
        }
    }

  private def waitForMillis(millis: Long) = {
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
