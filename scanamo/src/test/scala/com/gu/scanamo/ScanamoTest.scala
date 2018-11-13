package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.generic.auto._

class ScanamoTest extends org.scalatest.FunSpec with org.scalatest.Matchers {
  it("should bring back all results for queries over large datasets") {
    val client = LocalDynamoDB.client()
    case class Large(name: String, number: Int, stuff: String)
    LocalDynamoDB.usingRandomTable(client)('name -> S, 'number -> N) { t =>
      Scanamo.putAll(client)(t)(
        (for { i <- 0 until 100 } yield Large("Harry", i, util.Random.nextString(5000))).toSet
      )
      Scanamo.put(client)(t)(Large("George", 1, "x"))
      import syntax._
      Scanamo.query[Large](client)(t)('name -> "Harry").toList.size should be(100)
    }
    client.shutdown()
  }

  it("should get consistently") {
    val client = LocalDynamoDB.client()
    case class City(name: String, country: String)
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      Scanamo.put(client)(t)(City("Nashville", "US"))

      import com.gu.scanamo.syntax._
      Scanamo.getWithConsistency[City](client)(t)('name -> "Nashville") should equal(
        Some(Right(City("Nashville", "US")))
      )
    }
    client.shutdown()
  }

  it("should get consistent") {
    case class City(name: String, country: String)

    val client = LocalDynamoDB.client()
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      import com.gu.scanamo.syntax._
      val cityTable = Table[City](t)
      val ops = for {
        _ <- cityTable.put(City("Nashville", "US"))
        res <- cityTable.consistently.get('name -> "Nashville")
      } yield res
      Scanamo.exec(client)(ops) should equal(Some(Right(City("Nashville", "US"))))
    }
    client.shutdown()
  }

  it("should delete all entries from a string set, and be able to read the dynamo item") {
    case class Person(name: String, pets: Set[String])

    val client = LocalDynamoDB.client()
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      import syntax._

      val personTable = Table[Person](t)
      val ops = for {
        _ <- personTable.put(Person("bob", Set("hamster")))
        _ <- personTable.update('name -> "bob", delete('pets -> Set("hamster")))
        res <- personTable.get('name -> "bob")
      } yield res
      Scanamo.exec(client)(ops) should equal(Some(Right(Person("bob", Set.empty))))
    }
    client.shutdown()
  }
}
