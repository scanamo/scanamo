package org.scanamo.ops.retrypolicy

import org.scanamo.ops.retrypolicy.RetryPolicy._

import scala.concurrent.duration._

sealed abstract class RetryPolicy extends Product with Serializable { self =>
  final val done: Boolean = self match {
    case Constant(_, numberOfRetries)       => numberOfRetries < 1
    case Linear(_, numberOfRetries, _)      => numberOfRetries < 1
    case Exponential(_, numberOfRetries, _) => numberOfRetries < 1
    case Max(0)                             => true
    case And(firstPolicy, secondPolicy)     => firstPolicy.done && secondPolicy.done
    case Or(firstPolicy, secondPolicy)      => firstPolicy.done || secondPolicy.done
    case Never                              => true
    case _                                  => false
  }

  final val delay: Long = self match {
    case Constant(retryDelay, _)  => retryDelay.toMillis
    case Linear(delay, _, factor) => delay.toMillis * factor.toLong
    case Exponential(delay, _, factor) =>
      val retryDelay = delay.toMillis
      retryDelay * Math.pow(retryDelay.toDouble, factor).toLong
    case And(firstPolicy, secondPolicy) => Math.max(firstPolicy.delay, secondPolicy.delay)
    case Or(firstPolicy, secondPolicy)  => Math.min(firstPolicy.delay, secondPolicy.delay)
    case _                              => 0
  }

  final def update: RetryPolicy = self match {
    case policy @ Constant(_, retries)       => policy.copy(numberOfRetries = retries - 1)
    case policy @ Linear(_, retries, _)      => policy.copy(numberOfRetries = retries - 1)
    case policy @ Exponential(_, retries, _) => policy.copy(numberOfRetries = retries - 1)
    case Max(retries)                        => Max(retries - 1)
    case And(firstPolicy, secondPolicy)      => And(firstPolicy.update, secondPolicy.update)
    case Or(firstPolicy, secondPolicy)       => Or(firstPolicy.update, secondPolicy.update)
    case retryPolicy                         => retryPolicy
  }

  final def &&(that: RetryPolicy): RetryPolicy = And(self, that)
  final def ||(that: RetryPolicy): RetryPolicy = Or(self, that)
}

object RetryPolicy {
  final case class Constant(retryDelay: FiniteDuration, numberOfRetries: Int) extends RetryPolicy
  final case class Linear(retryDelay: FiniteDuration, numberOfRetries: Int, factor: Double) extends RetryPolicy
  final case class Exponential(retryDelay: FiniteDuration, numberOfRetries: Int, factor: Double) extends RetryPolicy
  final case class Max(numberOfRetries: Int) extends RetryPolicy
  final case class And(firstPolicy: RetryPolicy, secondPolicy: RetryPolicy) extends RetryPolicy
  final case class Or(firstPolicy: RetryPolicy, secondPolicy: RetryPolicy) extends RetryPolicy
  final case object Always extends RetryPolicy
  final case object Never extends RetryPolicy
}
