package com.gu.scanamo

import java.util

import cats._
import cats.data.State
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryResult, ScanResult}
import com.gu.scanamo.ops.{BatchGet, BatchWrite, Query, _}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.convert.decorateAll._

class ScanamoFreeTest extends FunSuite with Matchers {
  test("only make one call for a limited result") {
    val limitedScan = ScanamoFree.scanWithLimit[Int]("x", 1)

    val countingInterpreter = new RequestCountingInterpreter()

    val numOps = limitedScan.foldMap(countingInterpreter).runEmptyS.value

    assert(numOps == 1)
  }

  test("unlimited scan, scans exhaustively") {
    val limitedScan = ScanamoFree.scan[Int]("x")

    val countingInterpreter = new RequestCountingInterpreter()

    val numOps = limitedScan.foldMap(countingInterpreter).runEmptyS.value

    assert(numOps == 42)
  }
}

class RequestCountingInterpreter extends (ScanamoOpsA ~> RequestCountingInterpreter.CountingState) {
  def apply[A](op: ScanamoOpsA[A]): RequestCountingInterpreter.CountingState[A] = op match {
    case Put(req) => ???
    case ConditionalPut(req) => ???
    case Get(req) => ???
    case Delete(req) => ???
    case ConditionalDelete(req) => ???
    case Scan(req) => State(counter =>
      if (counter < 42)
        counter + 1 -> new ScanResult().withLastEvaluatedKey(Map("x" -> DynamoFormat[Int].write(1)).asJava)
          .withItems(List.fill(Option(req.getLimit).map(_.toInt).getOrElse(50))(
            new util.HashMap[String, AttributeValue]()): _*)
      else
        counter -> new ScanResult().withItems(List.empty[java.util.Map[String, AttributeValue]].asJava)
    )
    case Query(req) => State(counter =>
      if (counter < 42)
        counter + 1 -> new QueryResult().withLastEvaluatedKey(Map("x" -> DynamoFormat[Int].write(1)).asJava)
            .withItems(List.fill(req.getLimit)(new util.HashMap[String, AttributeValue]()): _*)
      else
        counter -> new QueryResult().withItems(List.empty[java.util.Map[String, AttributeValue]].asJava)
    )
    case BatchWrite(req) => ???
    case BatchGet(req) => ???
    case Update(req) => ???
    case ConditionalUpdate(req) => ???
  }
}

object RequestCountingInterpreter {
  type CountingState[V] = State[Int, V]
}
