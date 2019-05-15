package org.scanamo.ops
package retrypolicy

import java.util.concurrent.Executors

import scala.concurrent.duration._

object DefaultRetryPolicy {
  final val defaultInitialDelay = 20.millis
  final val defaultFactor = 2
  final val defaultRetries = 10
  final val numberOfThreads = 1
  final val defaultScheduler = Executors.newScheduledThreadPool(numberOfThreads)
  final val defaultPolicy = RetryPolicy.Linear(defaultInitialDelay, defaultFactor, defaultRetries)
}
