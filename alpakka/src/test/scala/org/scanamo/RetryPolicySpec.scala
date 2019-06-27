package org.scanamo

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.{ Assertion, AsyncFreeSpec, BeforeAndAfterAll }
import org.scanamo.ops.retrypolicy.{ RetryPolicy, WithRetry }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.Success

class ScanamoIssue extends AsyncFreeSpec with BeforeAndAfterAll with WithRetry {

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override protected def afterAll(): Unit = {
    materializer.shutdown()
    Await.ready(actorSystem.terminate(), 10.seconds)
  }

  private val tries = 5
  private val delay = 10.millis
  private val factor = 1.2

  private def testcase(policy: RetryPolicy): Future[Assertion] =
    retry(Source.failed(new LimitExceededException("Limit Exceeded")), policy).recover {
      case NonFatal(t) => t
    }.runWith(Sink.seq)
      .map(x => assert(x.size == 1))

  private def testcaseWithDelay(policy: RetryPolicy): Future[Assertion] = {
    val minDelay = tries * delay.toMillis
    val start = System.currentTimeMillis()
    val future = testcase(policy)
    future transform { _ =>
      val stop = System.currentTimeMillis()
      Success(assert(stop - start > minDelay))
    }
  }

  "Retry Policies should terminate" - {
    "Plain maximum" in testcase(RetryPolicy.Max(tries))
    "Constant delay AND maximum" in testcaseWithDelay(RetryPolicy.Max(tries) && RetryPolicy.Constant(delay))
    "Linear delay AND maximum" in testcaseWithDelay(RetryPolicy.Max(tries) && RetryPolicy.Linear(delay, factor))
    "Exponential delay AND maximum" in testcase(RetryPolicy.Max(tries) && RetryPolicy.Exponential(delay, factor))
    // "Constant delay OR maximum" in testcaseWithDelay(RetryPolicy.Max(tries) || RetryPolicy.Max(tries * 2) && RetryPolicy.Constant(delay))
    // "Linear delay OR maximum" in testcaseWithDelay(RetryPolicy.Max(tries) || RetryPolicy.Max(tries * 2) && RetryPolicy.Linear(delay, factor))
    // "Exponential delay OR maximum" in testcase(RetryPolicy.Max(tries) || RetryPolicy.Max(tries * 2) && RetryPolicy.Exponential(delay, factor))
  }
}
