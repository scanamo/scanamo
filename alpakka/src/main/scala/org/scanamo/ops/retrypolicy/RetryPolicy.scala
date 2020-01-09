/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo.ops.retrypolicy

import java.util.concurrent.ThreadLocalRandom
import org.scanamo.ops.retrypolicy.RetryPolicy._
import scala.concurrent.duration.Duration

sealed abstract class RetryPolicy extends Product with Serializable { self =>
  final def continue: Boolean = self match {
    case Max(0) | Never              => false
    case And(thisPolicy, thatPolicy) => thisPolicy.continue && thatPolicy.continue
    case Or(thisPolicy, thatPolicy)  => thisPolicy.continue || thatPolicy.continue
    case _                           => true
  }

  final def delay: Long = self match {
    case Constant(retryDelay, min, max) =>
      val currentRetryDelay = retryDelay.toMillis
      getDelayWithJitter(currentRetryDelay, min, max)
    case Linear(retryDelay, factor, min, max) =>
      val currentRetryDelay = retryDelay.toMillis * factor.toLong
      getDelayWithJitter(currentRetryDelay, min, max)
    case Exponential(retryDelay, factor, min, max, _) =>
      val delayMillis = retryDelay.toMillis
      val currentRetryDelay = delayMillis * Math.pow(delayMillis.toDouble, factor).toLong
      getDelayWithJitter(currentRetryDelay, min, max)
    case And(thisPolicy, thatPolicy) => Math.max(thisPolicy.delay, thatPolicy.delay)
    case Or(thisPolicy, thatPolicy)  => Math.min(thisPolicy.delay, thatPolicy.delay)
    case _                           => 0
  }

  final def update: RetryPolicy = self match {
    case Max(retries) if retries > 0                  => Max(retries - 1)
    case And(thisPolicy, thatPolicy)                  => And(thisPolicy.update, thatPolicy.update)
    case Or(thisPolicy, thatPolicy)                   => Or(thisPolicy.update, thatPolicy.update)
    case Linear(retryDelay, n, min, max)              => Linear(retryDelay, n + 1, min, max)
    case Exponential(retryDelay, factor, n, min, max) => Exponential(retryDelay, factor, n + 1, min, max)
    case retryPolicy                                  => retryPolicy
  }

  final def &&(that: RetryPolicy): RetryPolicy = And(self, that)
  final def ||(that: RetryPolicy): RetryPolicy = Or(self, that)

  final private def getDelayWithJitter(delayInMillis: Long, lowerBound: Long, upperBound: Long) = {
    val start = delayInMillis - lowerBound
    val finish = delayInMillis + upperBound + 1
    ThreadLocalRandom.current().nextLong(start, finish)
  }
}

object RetryPolicy {
  final private case class Constant(retryDelay: Duration, min: Long, max: Long) extends RetryPolicy
  final private case class Linear(retryDelay: Duration, n: Int, min: Long, max: Long) extends RetryPolicy
  final private case class Exponential(retryDelay: Duration, factor: Double, min: Long, max: Long, n: Int)
      extends RetryPolicy
  final private case class Max(numberOfRetries: Int) extends RetryPolicy
  final private case class And(thisPolicy: RetryPolicy, thatPolicy: RetryPolicy) extends RetryPolicy
  final private case class Or(thisPolicy: RetryPolicy, thatPolicy: RetryPolicy) extends RetryPolicy
  final private case object Always extends RetryPolicy
  final private case object Never extends RetryPolicy

  /** The policy that always retries immediately */
  val always: RetryPolicy = Always

  /** The policy that never retries */
  val never: RetryPolicy = Never

  /** The policy that retries once immediately */
  val once: RetryPolicy = Max(1)

  /** The policy that retries `n` times */
  def max(n: Int): RetryPolicy = if (n <= 0) never else Max(n)

  /** The policy that always retries, waiting `base` between each attempt */
  def fixed(base: Duration, min: Long = 0, max: Long = 0): RetryPolicy =
    if (base < Duration.Zero) never
    else if (base == Duration.Zero) always
    else Constant(base, min, max)

  /** The policy that always retries, waiting `base * n` where `n` is the number of attempts */
  def linear(base: Duration, min: Long = 0, max: Long = 0): RetryPolicy =
    if (base < Duration.Zero) never
    else if (base == Duration.Zero) always
    else Linear(base, 1, min, max)

  /** The policy that always retries, waiting `base * factor ^ n` where `n` is the number of attempts */
  def exponential(base: Duration, factor: Double = 2, min: Long = 0, max: Long = 0): RetryPolicy =
    if (base < Duration.Zero) never
    else if (base == Duration.Zero) always
    else Exponential(base, factor, min, max, 0)
}
