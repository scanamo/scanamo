package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._

class ScanamoTest extends org.scalatest.FunSpec with org.scalatest.Matchers {
  it("should bring back all results for queries over large datasets") {
    val client = LocalDynamoDB.client()
    LocalDynamoDB.createTable(client)("large-query")('name -> S, 'number -> N)

    case class Large(name: String, number: Int, stuff: String)
    Scanamo.putAll(client)("large-query")(
      (for { i <- 0 until 100 } yield Large("Harry", i, util.Random.nextString(5000))).toList
    )
    Scanamo.put(client)("large-query")(Large("George", 1, "x"))
    import syntax._
    Scanamo.query[Large](client)("large-query")('name -> "Harry").toList.size should be (100)

    val deleteResult = client.deleteTable("large-query")
  }
}
