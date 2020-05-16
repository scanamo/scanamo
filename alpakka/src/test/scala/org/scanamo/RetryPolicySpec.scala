package org.scanamo

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import software.amazon.awssdk.services.dynamodb.model._
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ Assertion, AsyncFreeSpec, BeforeAndAfterAll }
import org.scanamo.ops.retrypolicy.{ RetryPolicy, WithRetry }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.control.{ NoStackTrace, NonFatal }

class RetryPolicySpec extends AsyncFreeSpec with BeforeAndAfterAll with WithRetry with Matchers {
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def retryable(throwable: Throwable): Boolean = ScanamoAlpakka.defaultRetryableCheck(throwable)

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
    future map { _ =>
      val stop = System.currentTimeMillis()
      assert(stop - start > minDelay)
    }
  }

  "Retry Policies should terminate" - {
    import RetryPolicy._

    "Plain maximum" in testcase(max(tries))
    "Constant delay AND maximum" in testcaseWithDelay(max(tries) && fixed(delay))
    "Linear delay AND maximum" in testcaseWithDelay(max(tries) && linear(delay))
    "Exponential delay AND maximum" in testcase(max(tries) && exponential(delay, factor))
    "Constant delay OR linear delay" in testcaseWithDelay(max(tries) && (fixed(delay) || linear(delay)))
    "Constant delay OR exponential delay" in testcaseWithDelay(max(tries) && (fixed(delay) || exponential(delay)))
  }

  "Retry Policies should check if exception is retryable" - {
    "Perform retry if true" in {
      var executedOnce = false
      def op: Source[String, NotUsed] =
        if (!executedOnce) {
          executedOnce = true
          Source.failed(new ProvisionedThroughputExceededException("Throughput Exceeded"))
        } else {
          Source.single("Success")
        }

      retry[String](op, RetryPolicy.once)
        .runWith(Sink.head)
        .map(x => x should equal("Success"))
    }

    "Skip retry if false" in {
      var executedOnce = false
      def op: Source[String, NotUsed] =
        if (!executedOnce) {
          executedOnce = true
          Source.failed(new RuntimeException("Don't retry me"))
        } else {
          Source.single("Fail, this should not be retried")
        }

      retry[String](op, RetryPolicy.once).recover { case _: RuntimeException => "Success" }
        .runWith(Sink.head)
        .map(x => x should equal("Success"))
    }
  }
}
