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
    LocalDynamoDB.usingTable(client)("asyncFarmers")('name -> S) {
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      import com.gu.scanamo.syntax._

      val result = for {
        _ <- ScanamoAsync.put(client)("asyncFarmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
      } yield Scanamo.get[Farmer](client)("asyncFarmers")('name -> "McDonald")

      result.futureValue should equal(Some(Valid(Farmer("McDonald", 156, Farm(List("sheep", "cow"))))))
    }
  }

  it("should get asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncFarmers")('name -> S) {
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      Scanamo.put(client)("asyncFarmers")(Farmer("Maggot", 75L, Farm(List("dog"))))

      ScanamoAsync.get[Farmer](client)("asyncFarmers")(UniqueKey(KeyEquals('name, "Maggot")))
        .futureValue should equal(Some(Valid(Farmer("Maggot", 75, Farm(List("dog"))))))

      import com.gu.scanamo.syntax._

      ScanamoAsync.get[Farmer](client)("asyncFarmers")('name -> "Maggot")
        .futureValue should equal(Some(Valid(Farmer("Maggot", 75, Farm(List("dog"))))))
    }

    LocalDynamoDB.usingTable(client)("asyncEngines")('name -> S, 'number -> N) {
      case class Engine(name: String, number: Int)

      Scanamo.put(client)("asyncEngines")(Engine("Thomas", 1))

      import com.gu.scanamo.syntax._
      ScanamoAsync.get[Engine](client)("asyncEngines")('name -> "Thomas" and 'number -> 1)
        .futureValue should equal(Some(Valid(Engine("Thomas", 1))))
    }
  }

  it("should delete asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncFarmers")('name -> S) {

      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      Scanamo.put(client)("asyncFarmers")(Farmer("McGregor", 62L, Farm(List("rabbit"))))

      import com.gu.scanamo.syntax._

      val maybeFarmer = for {
        _ <- ScanamoAsync.delete(client)("asyncFarmers")('name -> "McGregor")
      } yield Scanamo.get[Farmer](client)("asyncFarmers")('name -> "McGregor")

      maybeFarmer.futureValue should equal(None)
    }
  }
  
  it("should scan asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncBears")('name -> S) {

      case class Bear(name: String, favouriteFood: String)

      Scanamo.put(client)("asyncBears")(Bear("Pooh", "honey"))
      Scanamo.put(client)("asyncBears")(Bear("Yogi", "picnic baskets"))

      ScanamoAsync.scan[Bear](client)("asyncBears").futureValue.toList should equal(
        List(Valid(Bear("Pooh", "honey")), Valid(Bear("Yogi", "picnic baskets")))
      )
    }

    LocalDynamoDB.usingTable(client)("asyncLemmings")('name -> S) {

      case class Lemming(name: String, stuff: String)

      Scanamo.putAll(client)("asyncLemmings")(
        (for {_ <- 0 until 100} yield Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toList
      )

      ScanamoAsync.scan[Lemming](client)("asyncLemmings").futureValue.toList.size should equal(100)
    }
  }

  it("should query asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncAnimals")('species -> S, 'number -> N) {

      case class Animal(species: String, number: Int)

      Scanamo.put(client)("asyncAnimals")(Animal("Wolf", 1))

      for {i <- 1 to 3} Scanamo.put(client)("asyncAnimals")(Animal("Pig", i))

      import com.gu.scanamo.syntax._

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig").futureValue.toList should equal(
        List(Valid(Animal("Pig", 1)), Valid(Animal("Pig", 2)), Valid(Animal("Pig", 3))))

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number < 3).futureValue.toList should equal(
        List(Valid(Animal("Pig", 1)), Valid(Animal("Pig", 2))))

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number > 1).futureValue.toList should equal(
        List(Valid(Animal("Pig", 2)), Valid(Animal("Pig", 3))))

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number <= 2).futureValue.toList should equal(
        List(Valid(Animal("Pig", 1)), Valid(Animal("Pig", 2))))

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number >= 2).futureValue.toList should equal(
        List(Valid(Animal("Pig", 2)), Valid(Animal("Pig", 3))))

    }

    LocalDynamoDB.usingTable(client)("asyncTransport")('mode -> S, 'line -> S) {

      case class Transport(mode: String, line: String)

      import com.gu.scanamo.syntax._

      Scanamo.putAll(client)("asyncTransport")(List(
        Transport("Underground", "Circle"),
        Transport("Underground", "Metropolitan"),
        Transport("Underground", "Central")))

      ScanamoAsync.query[Transport](client)("asyncTransport")('mode -> "Underground" and ('line beginsWith "C")).futureValue.toList should equal(
        List(Valid(Transport("Underground", "Central")), Valid(Transport("Underground", "Circle"))))
    }
  }
  
  it("should put multiple items asynchronously") {
    case class Rabbit(name: String)

    LocalDynamoDB.usingTable(client)("asyncRabbits")('name -> S) {
      val result = for {
        _ <- ScanamoAsync.putAll(client)("asyncRabbits")((
          for {_ <- 0 until 100} yield Rabbit(util.Random.nextString(500))
          ).toList)
      } yield Scanamo.scan[Rabbit](client)("asyncRabbits")

      result.futureValue.toList.size should equal(100)
    }
    ()
  }

  it("should get multiple items asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncFarmers")('name -> S) {

      case class Farm(animals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      Scanamo.putAll(client)("asyncFarmers")(List(
        Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
      ))

      ScanamoAsync.getAll[Farmer](client)("asyncFarmers")(
        UniqueKeys(KeyList('name, List("Boggis", "Bean")))
      ).futureValue should equal(
        List(Valid(Farmer("Boggis", 43, Farm(List("chicken")))), Valid(Farmer("Bean", 55, Farm(List("turkey"))))))

      import com.gu.scanamo.syntax._

      ScanamoAsync.getAll[Farmer](client)("asyncFarmers")('name -> List("Boggis", "Bean")).futureValue should equal(
        List(Valid(Farmer("Boggis", 43, Farm(List("chicken")))), Valid(Farmer("Bean", 55, Farm(List("turkey"))))))
    }

    LocalDynamoDB.usingTable(client)("asyncDoctors")('actor -> S, 'regeneration -> N) {
      case class Doctor(actor: String, regeneration: Int)

      Scanamo.putAll(client)("asyncDoctors")(
        List(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))

      import com.gu.scanamo.syntax._
      ScanamoAsync.getAll[Doctor](client)("asyncDoctors")(
        ('actor and 'regeneration) -> List("McCoy" -> 9, "Ecclestone" -> 11)
      ).futureValue should equal(
        List(Valid(Doctor("McCoy", 9)), Valid(Doctor("Ecclestone", 11))))

    }
  }
}
