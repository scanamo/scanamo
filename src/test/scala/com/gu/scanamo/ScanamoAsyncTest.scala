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
    case class Farm(asyncAnimals: List[String])
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
    case class Farm(asyncAnimals: List[String])
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

    case class Farm(asyncAnimals: List[String])

    case class Farmer(name: String, age: Long, farm: Farm)

    val putResult = Scanamo.put(client)("asyncFarmers")(Farmer("McGregor", 62L, Farm(List("rabbit"))))

    import com.gu.scanamo.syntax._

    val deleteResult = ScanamoAsync.delete(client)("asyncFarmers")('name -> "McGregor")

    val maybeFarmer = for (_ <-  deleteResult) yield Scanamo.get[Farmer](client)("asyncFarmers")('name -> "McGregor")

    maybeFarmer.futureValue should equal(None)

    client.deleteTable("asyncFarmers")
    ()
  }
  
  it("should scan asynchronously") {
    LocalDynamoDB.createTable(client)("asyncBears")('name -> S)

    case class Bear(name: String, favouriteFood: String)

    val r1 = Scanamo.put(client)("asyncBears")(Bear("Pooh", "honey"))

    val r2 = Scanamo.put(client)("asyncBears")(Bear("Yogi", "picnic baskets"))

    ScanamoAsync.scan[Bear](client)("asyncBears").futureValue.toList should equal(
      List(Valid(Bear("Pooh","honey")), Valid(Bear("Yogi","picnic baskets")))
    )

    client.deleteTable("asyncBears")

    LocalDynamoDB.createTable(client)("asyncLemmings")('name -> S)

    case class Lemming(name: String, stuff: String)

    val lemmingResults = Scanamo.putAll(client)("asyncLemmings")(
      (for { _ <- 0 until 100 } yield Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toList
    )

    ScanamoAsync.scan[Lemming](client)("asyncLemmings").futureValue.toList.size should equal(100)

    client.deleteTable("asyncLemmings")
    ()
  }

  it("should query asynchronously") {
    LocalDynamoDB.createTable(client)("asyncAnimals")('species -> S, 'number -> N)

    case class Animal(species: String, number: Int)

    val r1 = Scanamo.put(client)("asyncAnimals")(Animal("Wolf", 1))

    val r2 = for { i <- 1 to 3 } Scanamo.put(client)("asyncAnimals")(Animal("Pig", i))

    import com.gu.scanamo.syntax._

    ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig").futureValue.toList should equal(
      List(Valid(Animal("Pig",1)), Valid(Animal("Pig",2)), Valid(Animal("Pig",3))))

    ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number < 3).futureValue.toList should equal(
      List(Valid(Animal("Pig",1)), Valid(Animal("Pig",2))))

    ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number > 1).futureValue.toList should equal(
      List(Valid(Animal("Pig",2)), Valid(Animal("Pig",3))))

    ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number <= 2).futureValue.toList should equal(
      List(Valid(Animal("Pig",1)), Valid(Animal("Pig",2))))

    ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number >= 2).futureValue.toList should equal(
      List(Valid(Animal("Pig",2)), Valid(Animal("Pig",3))))

    client.deleteTable("asyncAnimals")

    LocalDynamoDB.createTable(client)("asyncTransport")('mode -> S, 'line -> S)

    case class Transport(mode: String, line: String)

    val lines = Scanamo.putAll(client)("asyncTransport")(List(
      Transport("Underground", "Circle"),
      Transport("Underground", "Metropolitan"),
      Transport("Underground", "Central")))

    ScanamoAsync.query[Transport](client)("asyncTransport")('mode -> "Underground" and ('line beginsWith "C")).futureValue.toList should equal(
      List(Valid(Transport("Underground","Central")), Valid(Transport("Underground","Circle"))))

    client.deleteTable("asyncTransport")
    ()
  }
}
