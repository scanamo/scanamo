package org.scanamo.ops.retrypolicy

import org.scanamo.ops.retrypolicy.RetryPolicy._

import scala.concurrent.duration.Duration

sealed abstract class RetryPolicy extends Product with Serializable { self =>
  final def continue: Boolean = self match {
    case Max(0) | Never              => false
    case And(thisPolicy, thatPolicy) => thisPolicy.continue && thatPolicy.continue
    case Or(thisPolicy, thatPolicy)  => thisPolicy.continue || thatPolicy.continue
    case _                           => true
  }

  final def delay: Duration = self match {
    case Constant(retryDelay)               => retryDelay
    case Linear(retryDelay, n)              => retryDelay * n.toDouble
    case Exponential(retryDelay, factor, n) => retryDelay * Math.pow(factor, n.toDouble)
    case And(thisPolicy, thatPolicy) =>
      val d1 = thisPolicy.delay
      val d2 = thatPolicy.delay
      if (d1 >= d2) d1 else d2
    case Or(thisPolicy, thatPolicy) =>
      val d1 = thisPolicy.delay
      val d2 = thatPolicy.delay
      if (d1 <= d2) d1 else d2
    case Never => Duration.Inf
    case _     => Duration.Zero
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
