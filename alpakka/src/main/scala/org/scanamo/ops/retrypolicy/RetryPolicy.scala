package org.scanamo.ops.retrypolicy

import java.util.concurrent.ScheduledExecutorService
import scala.concurrent.duration.FiniteDuration

sealed trait RetryPolicy

case class Exponential(initialDelay : FiniteDuration, factor : Int, retries : Int, scheduler : ScheduledExecutorService) extends RetryPolicy {
    require(factor > 1 & retries > 0)
}

case class Linear(initialDelay : FiniteDuration, retries : Int, scheduler : ScheduledExecutorService) extends RetryPolicy {
    require(retries > 0)
}

case object Default extends RetryPolicy
