package com.gu.scanamo

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FunSpec, Matchers}
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.query._

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

      result.futureValue should equal(Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "cow"))))))
    }
  }

  it("should get asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncFarmers")('name -> S) {
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      Scanamo.put(client)("asyncFarmers")(Farmer("Maggot", 75L, Farm(List("dog"))))

      ScanamoAsync.get[Farmer](client)("asyncFarmers")(UniqueKey(KeyEquals('name, "Maggot")))
        .futureValue should equal(Some(Right(Farmer("Maggot", 75, Farm(List("dog"))))))

      import com.gu.scanamo.syntax._

      ScanamoAsync.get[Farmer](client)("asyncFarmers")('name -> "Maggot")
        .futureValue should equal(Some(Right(Farmer("Maggot", 75, Farm(List("dog"))))))
    }

    LocalDynamoDB.usingTable(client)("asyncEngines")('name -> S, 'number -> N) {
      case class Engine(name: String, number: Int)

      Scanamo.put(client)("asyncEngines")(Engine("Thomas", 1))

      import com.gu.scanamo.syntax._
      ScanamoAsync.get[Engine](client)("asyncEngines")('name -> "Thomas" and 'number -> 1)
        .futureValue should equal(Some(Right(Engine("Thomas", 1))))
    }
  }

  it("should get consistently asynchronously") {
    case class City(name: String, country: String)
    LocalDynamoDB.usingTable(client)("asyncCities")('name -> S) {

      ScanamoAsync.put(client)("asyncCities")(City("Nashville", "US"))

      import com.gu.scanamo.syntax._
      ScanamoAsync.getWithConsistency[City](client)("asyncCities")('name -> "Nashville")
        .futureValue should equal(Some(Right(City("Nashville", "US"))))
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

  it("should deleteAll asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncFarmers")('name -> S) {

      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      import com.gu.scanamo.syntax._

      val dataSet = Set(
        Farmer("Patty", 200L, Farm(List("unicorn"))),
        Farmer("Ted", 40L, Farm(List("T-Rex"))),
        Farmer("Jack", 2L, Farm(List("velociraptor")))
      )

      Scanamo.putAll(client)("asyncFarmers")(dataSet)

      val maybeFarmer = for {
        _ <- ScanamoAsync.deleteAll(client)("asyncFarmers")('name -> dataSet.map(_.name))
      } yield Scanamo.scan[Farmer](client)("asyncFarmers")

      maybeFarmer.futureValue should equal(List.empty)
    }
  }

  it("should update asynchronously") {
    LocalDynamoDB.usingTable(client)("forecast")('location -> S) {

      case class Forecast(location: String, weather: String)

      Scanamo.put(client)("forecast")(Forecast("London", "Rain"))

      import com.gu.scanamo.syntax._

      val forecasts = for {
        _ <- ScanamoAsync.update(client)("forecast")('location -> "London", set('weather -> "Sun"))
      } yield Scanamo.scan[Forecast](client)("forecast")

      forecasts.futureValue should equal(List(Right(Forecast("London", "Sun"))))
    }
  }

  it("should update asynchornously if a condition holds") {
    LocalDynamoDB.usingTable(client)("forecast")('location -> S) {

      case class Forecast(location: String, weather: String, equipment: Option[String])

      val forecasts = Table[Forecast]("forecast")

      import com.gu.scanamo.syntax._

      val ops = for {
        _ <- forecasts.putAll(Set(Forecast("London", "Rain", None), Forecast("Birmingham", "Sun", None)))
        _ <- forecasts.given('weather -> "Rain").update('location -> "London", set('equipment -> Some("umbrella")))
        _ <- forecasts.given('weather -> "Rain").update('location -> "Birmingham", set('equipment -> Some("umbrella")))
        results <- forecasts.scan()
      } yield results

      ScanamoAsync.exec(client)(ops).futureValue should equal(
        List(Right(Forecast("London", "Rain", Some("umbrella"))), Right(Forecast("Birmingham", "Sun", None))))
    }
  }

  it("should scan asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncBears")('name -> S) {

      case class Bear(name: String, favouriteFood: String)

      Scanamo.put(client)("asyncBears")(Bear("Pooh", "honey"))
      Scanamo.put(client)("asyncBears")(Bear("Yogi", "picnic baskets"))

      ScanamoAsync.scan[Bear](client)("asyncBears").futureValue.toList should equal(
        List(Right(Bear("Pooh", "honey")), Right(Bear("Yogi", "picnic baskets")))
      )
    }

    LocalDynamoDB.usingTable(client)("asyncLemmings")('name -> S) {

      case class Lemming(name: String, stuff: String)

      Scanamo.putAll(client)("asyncLemmings")(
        (for {_ <- 0 until 100} yield Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toSet
      )

      ScanamoAsync.scan[Lemming](client)("asyncLemmings").futureValue.toList.size should equal(100)
    }
  }

  it("scans with a limit asynchronously") {
    case class Bear(name: String, favouriteFood: String)

    LocalDynamoDB.usingTable(client)("asyncBears")('name -> S) {
      Scanamo.put(client)("asyncBears")(Bear("Pooh", "honey"))
      Scanamo.put(client)("asyncBears")(Bear("Yogi", "picnic baskets"))
      val results = ScanamoAsync.scanWithLimit[Bear](client)("asyncBears", 1)
      results.futureValue should equal(List(Right(Bear("Pooh","honey"))))
    }
  }

  it ("scanIndexWithLimit") {
    case class Bear(name: String, favouriteFood: String, alias: Option[String])

    LocalDynamoDB.withTableWithSecondaryIndex(client)("asyncBears", "alias-index")('name -> S)('alias -> S) {
      Scanamo.put(client)("asyncBears")(Bear("Pooh", "honey", Some("Winnie")))
      Scanamo.put(client)("asyncBears")(Bear("Yogi", "picnic baskets", None))
      Scanamo.put(client)("asyncBears")(Bear("Graham", "quinoa", Some("Guardianista")))
      val results = ScanamoAsync.scanIndexWithLimit[Bear](client)("asyncBears", "alias-index", 1)
      results.futureValue should equal(List(Right(Bear("Graham","quinoa",Some("Guardianista")))))
    }
  }

  it("should query asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncAnimals")('species -> S, 'number -> N) {

      case class Animal(species: String, number: Int)

      Scanamo.put(client)("asyncAnimals")(Animal("Wolf", 1))

      for {i <- 1 to 3} Scanamo.put(client)("asyncAnimals")(Animal("Pig", i))

      import com.gu.scanamo.syntax._

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig").futureValue.toList should equal(
        List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2)), Right(Animal("Pig", 3))))

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number < 3).futureValue.toList should equal(
        List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2))))

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number > 1).futureValue.toList should equal(
        List(Right(Animal("Pig", 2)), Right(Animal("Pig", 3))))

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number <= 2).futureValue.toList should equal(
        List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2))))

      ScanamoAsync.query[Animal](client)("asyncAnimals")('species -> "Pig" and 'number >= 2).futureValue.toList should equal(
        List(Right(Animal("Pig", 2)), Right(Animal("Pig", 3))))

    }

    LocalDynamoDB.usingTable(client)("asyncTransport")('mode -> S, 'line -> S) {

      case class Transport(mode: String, line: String)

      import com.gu.scanamo.syntax._

      Scanamo.putAll(client)("asyncTransport")(Set(
        Transport("Underground", "Circle"),
        Transport("Underground", "Metropolitan"),
        Transport("Underground", "Central")))

      ScanamoAsync.query[Transport](client)("asyncTransport")('mode -> "Underground" and ('line beginsWith "C")).futureValue.toList should equal(
        List(Right(Transport("Underground", "Central")), Right(Transport("Underground", "Circle"))))
    }
  }

  it ("queries with a limit asynchronously") {
    import com.gu.scanamo.syntax._

    case class Transport(mode: String, line: String)

    LocalDynamoDB.withTable(client)("transport")('mode -> S, 'line -> S) {
      Scanamo.putAll(client)("transport")(Set(
        Transport("Underground", "Circle"),
        Transport("Underground", "Metropolitan"),
        Transport("Underground", "Central")))
      val results = ScanamoAsync.queryWithLimit[Transport](client)("transport")('mode -> "Underground" and ('line beginsWith "C"), 1)
      results.futureValue should equal(List(Right(Transport("Underground","Central"))))
    }
  }

  it ("queries an index with a limit asynchronously") {
    case class Transport(mode: String, line: String, colour: String)

    import com.gu.scanamo.syntax._

    LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")(
      'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    ) {
      Scanamo.putAll(client)("transport")(Set(
        Transport("Underground", "Circle", "Yellow"),
        Transport("Underground", "Metropolitan", "Magenta"),
        Transport("Underground", "Central", "Red"),
        Transport("Underground", "Picadilly", "Blue"),
        Transport("Underground", "Northern", "Black")))
      val results = ScanamoAsync.queryIndexWithLimit[Transport](client)("transport", "colour-index")(
        ('mode -> "Underground" and ('colour beginsWith "Bl")), 1)

      results.futureValue should equal(List(Right(Transport("Underground","Northern","Black"))))
    }
  }

  it("should put multiple items asynchronously") {
    case class Rabbit(name: String)

    LocalDynamoDB.usingTable(client)("asyncRabbits")('name -> S) {
      val result = for {
        _ <- ScanamoAsync.putAll(client)("asyncRabbits")((
          for {_ <- 0 until 100} yield Rabbit(util.Random.nextString(500))
          ).toSet)
      } yield Scanamo.scan[Rabbit](client)("asyncRabbits")

      result.futureValue.toList.size should equal(100)
    }
    ()
  }

  it("should get multiple items asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncFarmers")('name -> S) {

      case class Farm(animals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      Scanamo.putAll(client)("asyncFarmers")(Set(
        Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
      ))

      ScanamoAsync.getAll[Farmer](client)("asyncFarmers")(
        UniqueKeys(KeyList('name, Set("Boggis", "Bean")))
      ).futureValue should equal(
        Set(Right(Farmer("Boggis", 43, Farm(List("chicken")))), Right(Farmer("Bean", 55, Farm(List("turkey"))))))

      import com.gu.scanamo.syntax._

      ScanamoAsync.getAll[Farmer](client)("asyncFarmers")('name -> Set("Boggis", "Bean")).futureValue should equal(
        Set(Right(Farmer("Boggis", 43, Farm(List("chicken")))), Right(Farmer("Bean", 55, Farm(List("turkey"))))))
    }

    LocalDynamoDB.usingTable(client)("asyncDoctors")('actor -> S, 'regeneration -> N) {
      case class Doctor(actor: String, regeneration: Int)

      Scanamo.putAll(client)("asyncDoctors")(
        Set(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))

      import com.gu.scanamo.syntax._
      ScanamoAsync.getAll[Doctor](client)("asyncDoctors")(
        ('actor and 'regeneration) -> Set("McCoy" -> 9, "Ecclestone" -> 11)
      ).futureValue should equal(
        Set(Right(Doctor("McCoy", 9)), Right(Doctor("Ecclestone", 11))))

    }
  }

  it("conditionally put asynchronously") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    import com.gu.scanamo.syntax._

    val farmersTable = Table[Farmer]("nursery-farmers")

    LocalDynamoDB.usingTable(client)("nursery-farmers")('name -> S) {
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        _ <- farmersTable.given('age -> 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"))))
        _ <- farmersTable.given('age -> 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"))))
        farmerWithNewStock <- farmersTable.get('name -> "McDonald")
      } yield farmerWithNewStock
      ScanamoAsync.exec(client)(farmerOps).futureValue should equal(
        Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "chicken"))))))
    }
  }

  it("conditionally delete asynchronously") {
    case class Gremlin(number: Int, wet: Boolean)

    import com.gu.scanamo.syntax._

    val gremlinsTable = Table[Gremlin]("gremlins")

    LocalDynamoDB.usingTable(client)("gremlins")('number -> N) {
      val ops = for {
        _ <- gremlinsTable.putAll(Set(Gremlin(1, false), Gremlin(2, true)))
        _ <- gremlinsTable.given('wet -> true).delete('number -> 1)
        _ <- gremlinsTable.given('wet -> true).delete('number -> 2)
        remainingGremlins <- gremlinsTable.scan()
      } yield remainingGremlins
      ScanamoAsync.exec(client)(ops).futureValue.toList should equal(List(Right(Gremlin(1,false))))
    }
  }
}
