package org.scanamo.ops.retrypolicy

import java.util.concurrent.Executors

import scala.concurrent.duration._

object DefaultRetryPolicy {

    final val defaultInitialDelay = 20.millis
    final val defaultFactor = 2
    final val defaultRetries = 10
    final val defaultScheduler = Executors.newScheduledThreadPool(1)
    final val defaultFixed = Exponential(20.millis, 2, 10, defaultScheduler)

    def getPolicy(retryPolicy: RetryPolicy) : Exponential =
        retryPolicy match {
            case Linear(initialDelay, retries, scheduler) => Exponential(initialDelay, 1, retries, scheduler)
            case exponential @ Exponential(_, _, _, _) => exponential
            case Default => defaultFixed
        }
}
