package org.scanamo.ops.retrypolicy

import org.scanamo.ops.retrypolicy.RetryPolicy._
import scala.concurrent.duration._

sealed trait RetryPolicy { self =>
  final val done: Boolean = self match {
    case Constant(_, numberOfRetries)       => numberOfRetries < 1
    case Linear(_, numberOfRetries, _)      => numberOfRetries < 1
    case Exponential(_, numberOfRetries, _) => numberOfRetries < 1
    case Max(0)                             => true
    case And(x, y)                          => x.done && y.done
    case Or(x, y)                           => x.done || y.done
    case _                                  => false
  }

  final val delay: Long = self match {
    case Constant(retryDelay, _)  => retryDelay.toMillis
    case Linear(delay, _, factor) => delay.toMillis * factor.toLong
    case Exponential(delay, _, factor) =>
      val retryDelay = delay.toMillis
      retryDelay * Math.pow(retryDelay.toDouble, factor).toLong
    case And(x, y) => Math.max(x.delay, y.delay)
    case Or(x, y)  => Math.min(x.delay, y.delay)
    case _         => 0
  }

  final def update: RetryPolicy = self match {
    case policy @ Constant(_, retries)       => policy.copy(numberOfRetries = retries - 1)
    case policy @ Linear(_, retries, _)      => policy.copy(numberOfRetries = retries - 1)
    case policy @ Exponential(_, retries, _) => policy.copy(numberOfRetries = retries - 1)
    case Max(retries)                        => Max(retries - 1)
    case And(x, y)                           => And(x.update, y.update)
    case Or(x, y)                            => Or(x.update, y.update)
  }
}

object RetryPolicy {
  final case class Constant(retryDelay: FiniteDuration, numberOfRetries: Int) extends RetryPolicy
  final case class Linear(retryDelay: FiniteDuration, numberOfRetries: Int, factor: Double) extends RetryPolicy
  final case class Exponential(retryDelay: FiniteDuration, numberOfRetries: Int, factor: Double) extends RetryPolicy
  final case class Max(numberOfRetries: Int) extends RetryPolicy
  final case class And(x: RetryPolicy, y: RetryPolicy) extends RetryPolicy
  final case class Or(x: RetryPolicy, y: RetryPolicy) extends RetryPolicy
}
