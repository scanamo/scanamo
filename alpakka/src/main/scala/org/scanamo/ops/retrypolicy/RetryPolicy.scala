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

import java.util.SplittableRandom

import org.scanamo.ops.retrypolicy.RetryPolicy._

import scala.concurrent.duration._

sealed abstract class RetryPolicy extends Product with Serializable { self =>
  final def continue: Boolean = self match {
    case Max(0) | Never              => false
    case And(thisPolicy, thatPolicy) => thisPolicy.continue && thatPolicy.continue
    case Or(thisPolicy, thatPolicy)  => thisPolicy.continue || thatPolicy.continue
    case Jitter(_, _, innerPolicy)   => innerPolicy.continue
    case _                           => true
  }

  final def delay: Duration = self match {
    case Constant(retryDelay)               => retryDelay
    case Linear(retryDelay, factor)         => retryDelay * factor.toDouble
    case Exponential(retryDelay, factor, n) => retryDelay * Math.pow(factor, n.toDouble)
    case Jitter(random, margin, innerPolicy) =>
      val delay = innerPolicy.delay.toMillis
      val (low, high) = (Math.max(0, delay - margin), delay + margin + 1)
      random.nextLong(low, high).millis
    case And(thisPolicy, thatPolicy) => thisPolicy.delay.max(thatPolicy.delay)
    case Or(thisPolicy, thatPolicy)  => thisPolicy.delay.min(thatPolicy.delay)
    case Never                       => Duration.Inf
    case _                           => Duration.Zero
  }

  final def update: RetryPolicy = self match {
    case Max(retries) if retries > 0         => Max(retries - 1)
    case And(thisPolicy, thatPolicy)         => And(thisPolicy.update, thatPolicy.update)
    case Or(thisPolicy, thatPolicy)          => Or(thisPolicy.update, thatPolicy.update)
    case Linear(retryDelay, n)               => Linear(retryDelay, n + 1)
    case Exponential(retryDelay, factor, n)  => Exponential(retryDelay, factor, n + 1)
    case Jitter(random, margin, innerPolicy) => Jitter(random, margin, innerPolicy.update)
    case retryPolicy                         => retryPolicy
  }

  final def &&(that: RetryPolicy): RetryPolicy = And(self, that)
  final def ||(that: RetryPolicy): RetryPolicy = Or(self, that)

  final def delayOrElse[A](or: => A)(f: FiniteDuration => A): A = self.delay match {
    case duration: FiniteDuration => f(duration)
    case _: Duration.Infinite     => or
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
  final private case class Jitter(random: SplittableRandom, margin: Long, inner: RetryPolicy) extends RetryPolicy

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

  def jitter(margin: Long, retryPolicy: RetryPolicy, random: SplittableRandom = new SplittableRandom()): RetryPolicy = {
    require(margin < 0, "Margin can not be less than zero")
    Jitter(random, margin, retryPolicy)
  }
}
