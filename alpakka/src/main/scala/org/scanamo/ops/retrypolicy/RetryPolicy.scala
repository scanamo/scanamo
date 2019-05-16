package org.scanamo.ops.retrypolicy

import org.scanamo.ops.retrypolicy.RetryPolicy._

import scala.concurrent.duration.FiniteDuration

sealed abstract class RetryPolicy extends Product with Serializable { self =>
  final val done: Boolean = self match {
    case Constant(_, numberOfRetries)       => numberOfRetries < 1
    case Linear(_, numberOfRetries, _)      => numberOfRetries < 1
    case Exponential(_, numberOfRetries, _) => numberOfRetries < 1
    case Max(0)                             => true
    case And(thisPolicy, thatPolicy)        => thisPolicy.done && thatPolicy.done
    case Or(thisPolicy, thatPolicy)         => thisPolicy.done || thatPolicy.done
    case Never                              => true
    case _                                  => false
  }

  final val delay: Long = self match {
    case Constant(retryDelay, _)       => retryDelay.toMillis
    case Linear(retryDelay, _, factor) => retryDelay.toMillis * factor.toLong
    case Exponential(retryDelay, _, factor) =>
      val delayMillis = retryDelay.toMillis
      delayMillis * Math.pow(delayMillis.toDouble, factor).toLong
    case And(thisPolicy, thatPolicy) => Math.max(thisPolicy.delay, thatPolicy.delay)
    case Or(thisPolicy, thatPolicy)  => Math.min(thisPolicy.delay, thatPolicy.delay)
    case _                           => 0
  }

  final def update: RetryPolicy = self match {
    case policy @ Constant(_, numberOfRetries)       => policy.copy(numberOfRetries = numberOfRetries - 1)
    case policy @ Linear(_, numberOfRetries, _)      => policy.copy(numberOfRetries = numberOfRetries - 1)
    case policy @ Exponential(_, numberOfRetries, _) => policy.copy(numberOfRetries = numberOfRetries - 1)
    case Max(retries)                                => Max(retries - 1)
    case And(thisPolicy, thatPolicy)                 => And(thisPolicy.update, thatPolicy.update)
    case Or(thisPolicy, thatPolicy)                  => Or(thisPolicy.update, thatPolicy.update)
    case retryPolicy                                 => retryPolicy
  }

  final def &&(that: RetryPolicy): RetryPolicy = And(self, that)
  final def ||(that: RetryPolicy): RetryPolicy = Or(self, that)
}

object RetryPolicy {
  final case class Constant(retryDelay: FiniteDuration, numberOfRetries: Int) extends RetryPolicy
  final case class Linear(retryDelay: FiniteDuration, numberOfRetries: Int, factor: Double) extends RetryPolicy
  final case class Exponential(retryDelay: FiniteDuration, numberOfRetries: Int, factor: Double) extends RetryPolicy
  final case class Max(numberOfRetries: Int) extends RetryPolicy
  final case class And(thisPolicy: RetryPolicy, thatPolicy: RetryPolicy) extends RetryPolicy
  final case class Or(thisPolicy: RetryPolicy, thatPolicy: RetryPolicy) extends RetryPolicy
  final case object Always extends RetryPolicy
  final case object Never extends RetryPolicy
}
