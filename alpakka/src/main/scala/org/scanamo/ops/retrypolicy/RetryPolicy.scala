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

import org.scanamo.ops.retrypolicy.RetryPolicy._

import scala.concurrent.duration.Duration

import scala.util.Random

sealed abstract class RetryPolicy extends Product with Serializable { self =>
  final def continue: Boolean = self match {
    case Max(0) | Never              => false
    case And(thisPolicy, thatPolicy) => thisPolicy.continue && thatPolicy.continue
    case Or(thisPolicy, thatPolicy)  => thisPolicy.continue || thatPolicy.continue
    case _                           => true
  }
  
  final def delay: Long = self match {
    case Constant(retryDelay) =>
      val currentRetryDelay = retryDelay.toMillis
      getDelayWithJitter(currentRetryDelay)
    case Linear(retryDelay, factor) =>
      val currentRetryDelay = retryDelay.toMillis * factor.toLong
      getDelayWithJitter(currentRetryDelay)
    case Exponential(retryDelay, factor, _) =>
      val delayMillis = retryDelay.toMillis
      val currentRetryDelay = delayMillis * Math.pow(delayMillis.toDouble, factor).toLong
      getDelayWithJitter(currentRetryDelay)
    case And(thisPolicy, thatPolicy) => Math.max(thisPolicy.delay, thatPolicy.delay)
    case Or(thisPolicy, thatPolicy)  => Math.min(thisPolicy.delay, thatPolicy.delay)
    case _                           => 0
  }

  final def update: RetryPolicy = self match {
    case Max(retries) if retries > 0        => Max(retries - 1)
    case And(thisPolicy, thatPolicy)        => And(thisPolicy.update, thatPolicy.update)
    case Or(thisPolicy, thatPolicy)         => Or(thisPolicy.update, thatPolicy.update)
    case Linear(retryDelay, n)              => Linear(retryDelay, n + 1)
    case Exponential(retryDelay, factor, n) => Exponential(retryDelay, factor, n + 1)
    case retryPolicy                        => retryPolicy
  }

  final def &&(that: RetryPolicy): RetryPolicy = And(self, that)
  final def ||(that: RetryPolicy): RetryPolicy = Or(self, that)

  final private def getDelayWithJitter(delayInMillis: Long) = {
    val maxJitter = Math.ceil(delayInMillis * 0.2).toInt
    delayInMillis + Random.nextInt(maxJitter)
  }
}

object RetryPolicy {
  final private case class Constant(retryDelay: Duration) extends RetryPolicy
  final private case class Linear(retryDelay: Duration, n: Int) extends RetryPolicy
  final private case class Exponential(retryDelay: Duration, factor: Double, n: Int) extends RetryPolicy
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
  def fixed(base: Duration): RetryPolicy =
    if (base < Duration.Zero) never
    else if (base == Duration.Zero) always
    else Constant(base)

  /** The policy that always retries, waiting `base * n` where `n` is the number of attempts */
  def linear(base: Duration): RetryPolicy =
    if (base < Duration.Zero) never
    else if (base == Duration.Zero) always
    else Linear(base, 1)

  /** The policy that always retries, waiting `base * factor ^ n` where `n` is the number of attempts */
  def exponential(base: Duration, factor: Double = 2): RetryPolicy =
    if (base < Duration.Zero) never
    else if (base == Duration.Zero) always
    else Exponential(base, factor, 0)
}
