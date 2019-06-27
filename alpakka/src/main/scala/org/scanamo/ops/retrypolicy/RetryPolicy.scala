package org.scanamo.ops.retrypolicy

import org.scanamo.ops.retrypolicy.RetryPolicy._

import scala.concurrent.duration.FiniteDuration

sealed abstract class RetryPolicy extends Product with Serializable { self =>
  final def continue: Boolean = self match {
    case Max(0) | Never              => false
    case And(thisPolicy, thatPolicy) => thisPolicy.continue && thatPolicy.continue
    case Or(thisPolicy, thatPolicy)  => thisPolicy.continue || thatPolicy.continue
    case _                           => true
  }

  final def delay: Long = self match {
    case Constant(retryDelay)            => retryDelay.toMillis
    case Linear(retryDelay, factor)      => retryDelay.toMillis * factor.toLong
    case Exponential(retryDelay, factor) => retryDelay.toMillis * factor.toLong
    case And(thisPolicy, thatPolicy)     => Math.max(thisPolicy.delay, thatPolicy.delay)
    case Or(thisPolicy, thatPolicy)      => Math.min(thisPolicy.delay, thatPolicy.delay)
    case _                               => 0
  }

  final def update: RetryPolicy = self match {
    case Max(retries) if retries > 0     => Max(retries - 1)
    case And(thisPolicy, thatPolicy)     => And(thisPolicy.update, thatPolicy.update)
    case Or(thisPolicy, thatPolicy)      => Or(thisPolicy.update, thatPolicy.update)
    case Linear(retryDelay, factor)      => Linear(retryDelay, factor + 1)
    case Exponential(retryDelay, factor) => Exponential(retryDelay, factor * 2)
    case retryPolicy                     => retryPolicy
  }

  final val retries: Int = self match {
    case Max(retries) => retries
    case And(x, y)    => Math.max(x.retries, y.retries)
    case Or(x, y)     => Math.min(x.retries, y.retries)
    case _            => 0
  }

  final def &&(that: RetryPolicy): RetryPolicy = And(self, that)
  final def ||(that: RetryPolicy): RetryPolicy = Or(self, that)
}

object RetryPolicy {
  final case class Constant(retryDelay: FiniteDuration) extends RetryPolicy
  final case class Linear(retryDelay: FiniteDuration, factor: Double) extends RetryPolicy
  final case class Exponential(retryDelay: FiniteDuration, factor: Double) extends RetryPolicy
  final case class Max(numberOfRetries: Int) extends RetryPolicy
  final case class And(thisPolicy: RetryPolicy, thatPolicy: RetryPolicy) extends RetryPolicy
  final case class Or(thisPolicy: RetryPolicy, thatPolicy: RetryPolicy) extends RetryPolicy
  final case object Always extends RetryPolicy
  final case object Never extends RetryPolicy
}
