package org.scanamo.ops.retrypolicy

import java.util.concurrent.Executors

import scala.concurrent.duration._

object DefaultRetryPolicy {

  final val defaultInitialDelay = 20.millis
  final val defaultFactor = 2
  final val defaultRetries = 10
  final val defaultScheduler = Executors.newScheduledThreadPool(1)
  final val defaultFixed = Linear(20.millis, 2, 10, defaultScheduler)

  def getPolicy(retryPolicy: RetryPolicy): Linear =
    retryPolicy match {
      case Constant(initialDelay, retries, scheduler) => Linear(initialDelay, 1, retries, scheduler)
      case exponential @ Linear(_, _, _, _)           => exponential
      case _ @DefaultRetry                            => defaultFixed
    }
}
