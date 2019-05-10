package org.scanamo

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

case class RetrySettings(initialDelay : FiniteDuration = 20.millis,
                         factor : Int = 2,
                         retries : Int = 10)
