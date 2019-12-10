package org.scanamo

import java.util

import cats._
import cats.data.State
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model.{ AttributeValue, QueryResult, ScanResult }
import org.scanamo.ops.{ BatchGet, BatchWrite, Query, _ }
import org.scalatest.{ FunSuite, Matchers }

import collection.JavaConverters._

class ScanamoFreeTest extends FunSuite with Matchers {
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
      State(
        counter =>
          if (counter < 42)
            counter + 1 -> new ScanResult()
              .withLastEvaluatedKey(Map("x" -> DynamoFormat[Int].write(1).toAttributeValue).asJava)
              .withItems(List.fill(req.options.limit.getOrElse(50))(new util.HashMap[String, AttributeValue]()): _*)
          else
            counter -> new ScanResult().withItems(List.empty[java.util.Map[String, AttributeValue]].asJava)
      )
    case Query(req) =>
      State(
        counter =>
          if (counter < 42)
            counter + 1 -> new QueryResult()
              .withLastEvaluatedKey(Map("x" -> DynamoFormat[Int].write(1).toAttributeValue).asJava)
              .withItems(List.fill(req.options.limit.getOrElse(0))(new util.HashMap[String, AttributeValue]()): _*)
          else
            counter -> new QueryResult().withItems(List.empty[java.util.Map[String, AttributeValue]].asJava)
      )
    case BatchWrite(_)         => ???
    case BatchGet(_)           => ???
    case Update(_)             => ???
    case ConditionalUpdate(_)  => ???
    case TransactWriteItems(_) => ???
  }
}

object RequestCountingInterpreter {
  type CountingState[V] = State[Int, V]
}
