package com.gu.scanamo

import cats.data.Validated.Valid
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FunSpec, Matchers}
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._

class ScanamoAsyncTest extends FunSpec with Matchers with ScalaFutures {
  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(15, Millis))

  val client = LocalDynamoDB.client()
  import scala.concurrent.ExecutionContext.Implicits.global

  it("should put asynchronously") {
    LocalDynamoDB.createTable(client)("asyncFarmers")('name -> S)
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    val putResult =
      ScanamoAsync.put(client)("asyncFarmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))

    import com.gu.scanamo.syntax._

    val result = for {
      _ <- putResult
    } yield Scanamo.get[Farmer](client)("asyncFarmers")('name -> "McDonald")

    result.futureValue should equal(Some(Valid(Farmer("McDonald",156,Farm(List("sheep", "cow"))))))

    client.deleteTable("asyncFarmers")
    ()
  }

  it("should get asynchronously") {
    LocalDynamoDB.createTable(client)("asyncFarmers")('name -> S)
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    val putResult = Scanamo.put(client)("asyncFarmers")(Farmer("Maggot", 75L, Farm(List("dog"))))

    ScanamoAsync.get[Farmer](client)("asyncFarmers")(UniqueKey(KeyEquals('name, "Maggot")))
      .futureValue should equal(Some(Valid(Farmer("Maggot",75,Farm(List("dog"))))))

    import com.gu.scanamo.syntax._

    ScanamoAsync.get[Farmer](client)("asyncFarmers")('name -> "Maggot")
      .futureValue should equal(Some(Valid(Farmer("Maggot",75,Farm(List("dog"))))))

    val createTableResult = LocalDynamoDB.createTable(client)("asyncEngines")('name -> S, 'number -> N)

    case class Engine(name: String, number: Int)

    val thomas = Scanamo.put(client)("asyncEngines")(Engine("Thomas", 1))

    ScanamoAsync.get[Engine](client)("asyncEngines")('name -> "Thomas" and 'number -> 1)
      .futureValue should equal(Some(Valid(Engine("Thomas",1))))

    client.deleteTable("asyncFarmers")
    ()
  }

  it("should delete asynchronously") {
    LocalDynamoDB.createTable(client)("asyncFarmers")('name -> S)

    case class Farm(animals: List[String])

    case class Farmer(name: String, age: Long, farm: Farm)

    val putResult = Scanamo.put(client)("asyncFarmers")(Farmer("McGregor", 62L, Farm(List("rabbit"))))

    import com.gu.scanamo.syntax._

    val deleteResult = ScanamoAsync.delete(client)("asyncFarmers")('name -> "McGregor")

    val maybeFarmer = for (_ <-  deleteResult) yield Scanamo.get[Farmer](client)("asyncFarmers")('name -> "McGregor")

    maybeFarmer.futureValue should equal(None)

    client.deleteTable("asyncFarmers")
    ()
  }
}
