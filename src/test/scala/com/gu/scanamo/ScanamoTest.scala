package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._

class ScanamoTest extends org.scalatest.FunSpec with org.scalatest.Matchers {
  it("should bring back all results for queries over large datasets") {
    val client = LocalDynamoDB.client()
    LocalDynamoDB.createTable(client)("large-query")('name -> S, 'number -> N)

    case class Large(name: String, number: Int, stuff: String)
    Scanamo.putAll(client)("large-query")(
      (for { i <- 0 until 100 } yield Large("Harry", i, util.Random.nextString(5000))).toSet
    )
    Scanamo.put(client)("large-query")(Large("George", 1, "x"))
    import syntax._
    Scanamo.query[Large](client)("large-query")('name -> "Harry").toList.size should be (100)

    val deleteResult = client.deleteTable("large-query")
  }

  it("should get consistently") {
    val client = LocalDynamoDB.client()
    case class City(name: String, country: String)
    LocalDynamoDB.usingTable(client)("asyncCities")('name -> S) {

      Scanamo.put(client)("asyncCities")(City("Nashville", "US"))

      import com.gu.scanamo.syntax._
      Scanamo.getWithConsistency[City](client)("asyncCities")('name -> "Nashville") should equal(Some(Right(City("Nashville", "US"))))
    }
  }

  it("should get consistent") {
    case class City(name: String, country: String)

    val cityTable = Table[City]("asyncCities")

    import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._

    val client = LocalDynamoDB.client()
    LocalDynamoDB.usingTable(client)("asyncCities")('name -> S) {
      import com.gu.scanamo.syntax._
      val ops = for {
        _ <- cityTable.put(City("Nashville", "US"))
        res <- cityTable.consistent.get('name -> "Nashville")
      } yield res
      Scanamo.exec(client)(ops) should equal(Some(Right(City("Nashville", "US"))))
    }
  }
}
