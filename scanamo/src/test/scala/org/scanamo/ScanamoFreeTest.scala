package org.scanamo

import java.util

import cats._
import cats.data.State
import cats.implicits._
import software.amazon.awssdk.services.dynamodb.model.{ AttributeValue, QueryResponse, ScanResponse }
import org.scanamo.ops.{ BatchGet, BatchWrite, Query, _ }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import collection.JavaConverters._

class ScanamoFreeTest extends AnyFunSuite with Matchers {
  test("unlimited scan, scans exhaustively") {
    val limitedScan = ScanamoFree.scan[Int]("x")

    val countingInterpreter = new RequestCountingInterpreter()

    val numOps = limitedScan.foldMap(countingInterpreter).runEmptyS.value

    assert(numOps == 42)
  }
}

class RequestCountingInterpreter extends (ScanamoOpsA ~> RequestCountingInterpreter.CountingState) {
  def apply[A](op: ScanamoOpsA[A]): RequestCountingInterpreter.CountingState[A] = op match {
    case Put(_)               => ???
    case ConditionalPut(_)    => ???
    case Get(_)               => ???
    case Delete(_)            => ???
    case ConditionalDelete(_) => ???
    case Scan(req) =>
      State(counter =>
        if (counter < 42)
          counter + 1 -> ScanResponse.builder
            .lastEvaluatedKey(Map("x" -> DynamoFormat[Int].write(1).toAttributeValue).asJava)
            .items(List.fill(req.options.limit.getOrElse(50))(new util.HashMap[String, AttributeValue]()): _*)
            .build
        else
          counter -> ScanResponse.builder.items(List.empty[java.util.Map[String, AttributeValue]].asJava).build
      )
    case Query(req) =>
      State(counter =>
        if (counter < 42)
          counter + 1 -> QueryResponse.builder
            .lastEvaluatedKey(Map("x" -> DynamoFormat[Int].write(1).toAttributeValue).asJava)
            .items(List.fill(req.options.limit.getOrElse(0))(new util.HashMap[String, AttributeValue]()): _*)
            .build
        else
          counter -> QueryResponse.builder.items(List.empty[java.util.Map[String, AttributeValue]].asJava).build
      )
    case BatchWrite(_)        => ???
    case BatchGet(_)          => ???
    case Update(_)            => ???
    case ConditionalUpdate(_) => ???
    case TransactWriteAll(_)  => ???
  }
}

object RequestCountingInterpreter {
  type CountingState[V] = State[Int, V]
}
