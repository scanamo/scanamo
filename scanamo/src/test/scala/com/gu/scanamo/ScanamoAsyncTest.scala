package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
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

      implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
      implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

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

      implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
      implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

      Scanamo.put(client)("asyncFarmers")(Farmer("Maggot", 75L, Farm(List("dog"))))

      ScanamoAsync.get[Farmer](client)("asyncFarmers")(UniqueKey(KeyEquals('name, "Maggot")))
        .futureValue should equal(Some(Right(Farmer("Maggot", 75, Farm(List("dog"))))))

      import com.gu.scanamo.syntax._

      ScanamoAsync.get[Farmer](client)("asyncFarmers")('name -> "Maggot")
        .futureValue should equal(Some(Right(Farmer("Maggot", 75, Farm(List("dog"))))))
    }

    LocalDynamoDB.usingTable(client)("asyncEngines")('name -> S, 'number -> N) {
      case class Engine(name: String, number: Int)

      implicit val format: DynamoFormat[Engine] = DerivedDynamoFormat.derive

      Scanamo.put(client)("asyncEngines")(Engine("Thomas", 1))

      import com.gu.scanamo.syntax._
      ScanamoAsync.get[Engine](client)("asyncEngines")('name -> "Thomas" and 'number -> 1)
        .futureValue should equal(Some(Right(Engine("Thomas", 1))))
    }
  }

  it("should get consistently asynchronously") {
    case class City(name: String, country: String)

    implicit val format: DynamoFormat[City] = DerivedDynamoFormat.derive

    LocalDynamoDB.usingTable(client)("asyncCities")('name -> S) {

      import com.gu.scanamo.syntax._
      ScanamoAsync.put(client)("asyncCities")(City("Nashville", "US")).andThen {
        case _ =>
          ScanamoAsync.getWithConsistency[City](client)("asyncCities")('name -> "Nashville")
            .futureValue should equal(Some(Right(City("Nashville", "US"))))
      }
    }
  }

  it("should delete asynchronously") {
    LocalDynamoDB.usingTable(client)("asyncFarmers")('name -> S) {

      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
      implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

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

      implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
      implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

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

      implicit val format: DynamoFormat[Forecast] = DerivedDynamoFormat.derive

      Scanamo.put(client)("forecast")(Forecast("London", "Rain"))

      import com.gu.scanamo.syntax._

      val forecasts = for {
        _ <- ScanamoAsync.update(client)("forecast")('location -> "London", set('weather -> "Sun"))
      } yield Scanamo.scan[Forecast](client)("forecast")

      forecasts.futureValue should equal(List(Right(Forecast("London", "Sun"))))
    }
  }

  it("should update asynchronously if a condition holds") {
    LocalDynamoDB.usingTable(client)("forecast")('location -> S) {

      case class Forecast(location: String, weather: String, equipment: Option[String])

      implicit val format: DynamoFormat[Forecast] = DerivedDynamoFormat.derive

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

      implicit val format: DynamoFormat[Bear] = DerivedDynamoFormat.derive

      Scanamo.put(client)("asyncBears")(Bear("Pooh", "honey"))
      Scanamo.put(client)("asyncBears")(Bear("Yogi", "picnic baskets"))

      ScanamoAsync.scan[Bear](client)("asyncBears").futureValue.toList should equal(
        List(Right(Bear("Pooh", "honey")), Right(Bear("Yogi", "picnic baskets")))
      )
    }

    LocalDynamoDB.usingTable(client)("asyncLemmings")('name -> S) {

      case class Lemming(name: String, stuff: String)

      implicit val format: DynamoFormat[Lemming] = DerivedDynamoFormat.derive

      Scanamo.putAll(client)("asyncLemmings")(
        (for {_ <- 0 until 100} yield Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toSet
      )

      ScanamoAsync.scan[Lemming](client)("asyncLemmings").futureValue.toList.size should equal(100)
    }
  }

  it("scans with a limit asynchronously") {
    case class Bear(name: String, favouriteFood: String)

    implicit val format: DynamoFormat[Bear] = DerivedDynamoFormat.derive

    LocalDynamoDB.usingTable(client)("asyncBears")('name -> S) {
      Scanamo.put(client)("asyncBears")(Bear("Pooh", "honey"))
      Scanamo.put(client)("asyncBears")(Bear("Yogi", "picnic baskets"))
      val results = ScanamoAsync.scanWithLimit[Bear](client)("asyncBears", 1)
      results.futureValue should equal(List(Right(Bear("Pooh","honey"))))
    }
  }

  it ("scanIndexWithLimit") {
    case class Bear(name: String, favouriteFood: String, alias: Option[String])

    implicit val format: DynamoFormat[Bear] = DerivedDynamoFormat.derive

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

      implicit val format: DynamoFormat[Animal] = DerivedDynamoFormat.derive

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

      implicit val format: DynamoFormat[Transport] = DerivedDynamoFormat.derive

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

    implicit val format: DynamoFormat[Transport] = DerivedDynamoFormat.derive

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

    implicit val format: DynamoFormat[Transport] = DerivedDynamoFormat.derive

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
        'mode -> "Underground" and ('colour beginsWith "Bl"), 1)

      results.futureValue should equal(List(Right(Transport("Underground","Northern","Black"))))
    }
  }

  it ("queries an index asynchronously with 'between' sort-key condition") {
    case class Station(mode: String, name: String, zone: Int)

    implicit val format: DynamoFormat[Station] = DerivedDynamoFormat.derive

    import com.gu.scanamo.syntax._

    def deletaAllStations(client: AmazonDynamoDBAsync, stations: Set[Station]) = {
      ScanamoAsync.delete(client)("stations")('mode -> "Underground")
      ScanamoAsync.deleteAll(client)("stations")(
        UniqueKeys(MultipleKeyList(('mode, 'name), stations.map(station => (station.mode, station.name))))
      )
    }
    val LiverpoolStreet = Station("Underground", "Liverpool Street", 1)
    val CamdenTown = Station("Underground", "Camden Town", 2)
    val GoldersGreen = Station("Underground", "Golders Green", 3)
    val Hainault = Station("Underground", "Hainault", 4)

    LocalDynamoDB.withTableWithSecondaryIndex(client)("stations", "zone-index")(
      'mode -> S, 'name -> S)('mode -> S, 'zone -> N
    ) {
      val stations = Set(LiverpoolStreet, CamdenTown, GoldersGreen, Hainault)
      Scanamo.putAll(client)("stations")(stations)
      val results1 = ScanamoAsync.queryIndex[Station](client)("stations", "zone-index")(
        'mode -> "Underground" and ('zone between (2 and 4)))

      results1.futureValue should equal(List(Right(CamdenTown), Right(GoldersGreen), Right(Hainault)))

      val maybeStations1 = for {_ <- deletaAllStations(client, stations)} yield Scanamo.scan[Station](client)("stations")
      maybeStations1.futureValue should equal(List.empty)

      Scanamo.putAll(client)("stations")(Set(LiverpoolStreet))
      val results2 = ScanamoAsync.queryIndex[Station](client)("stations", "zone-index")(
        'mode -> "Underground" and ('zone between (2 and 4)))
      results2.futureValue should equal(List.empty)

      val maybeStations2 = for {_ <- deletaAllStations(client, stations)} yield Scanamo.scan[Station](client)("stations")
      maybeStations2.futureValue should equal(List.empty)

      Scanamo.putAll(client)("stations")(Set(CamdenTown))
      val results3 = ScanamoAsync.queryIndex[Station](client)("stations", "zone-index")(
        'mode -> "Underground" and ('zone between (1 and 1)))
      results3.futureValue should equal(List.empty)
    }
  }

  it("queries for items that are missing an attribute") {
      case class Farmer(firstName: String, surname: String, age: Option[Int])

      implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

      import com.gu.scanamo.syntax._

      val farmersTable = Table[Farmer]("nursery-farmers")

      LocalDynamoDB.usingTable(client)("nursery-farmers")('firstName -> S, 'surname -> S) {
        val farmerOps = for {
          _ <- farmersTable.put(Farmer("Fred", "Perry", None))
          _ <- farmersTable.put(Farmer("Fred", "McDonald", Some(54)))
          farmerWithNoAge <- farmersTable.filter(attributeNotExists('age)).query('firstName -> "Fred")
        } yield farmerWithNoAge
        ScanamoAsync.exec(client)(farmerOps).futureValue should equal(List(Right(Farmer("Fred", "Perry", None))))
      }
  }
  
  it("should put multiple items asynchronously") {
    case class Rabbit(name: String)

    implicit val format: DynamoFormat[Rabbit] = DerivedDynamoFormat.derive

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

      implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
      implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

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

      implicit val format: DynamoFormat[Doctor] = DerivedDynamoFormat.derive

      Scanamo.putAll(client)("asyncDoctors")(
        Set(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))

      import com.gu.scanamo.syntax._
      ScanamoAsync.getAll[Doctor](client)("asyncDoctors")(
        ('actor and 'regeneration) -> Set("McCoy" -> 9, "Ecclestone" -> 11)
      ).futureValue should equal(
        Set(Right(Doctor("McCoy", 9)), Right(Doctor("Ecclestone", 11))))

    }
  }

  it("should get multiple items asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingTable(client)("asyncFarms")('id -> N) {

      case class Farm(id: Int, name: String)
      implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
      val farms = (1 to 101).map(i => Farm(i, s"Farm #$i")).toSet

      Scanamo.putAll(client)("asyncFarms")(farms)

      ScanamoAsync.getAll[Farm](client)("asyncFarms")(
        UniqueKeys(KeyList('id, farms.map(_.id)))
      ).futureValue should equal(farms.map(Right(_)))
    }
  }

  it("should get multiple items consistently asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingTable(client)("asyncFarms")('id -> N) {

      case class Farm(id: Int, name: String)
      implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
      val farms = (1 to 101).map(i => Farm(i, s"Farm #$i")).toSet

      Scanamo.putAll(client)("asyncFarms")(farms)

      ScanamoAsync.getAllWithConsistency[Farm](client)("asyncFarms")(
        UniqueKeys(KeyList('id, farms.map(_.id)))
      ).futureValue should equal(farms.map(Right(_)))
    }
  }

  it("should return old item after put asynchronously") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
    implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

    val farmersTable = Table[Farmer]("nursery-farmers")

    LocalDynamoDB.usingTable(client)("nursery-farmers")('name -> S) {
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        result <- farmersTable.put(Farmer("McDonald", 50L, Farm(List("chicken", "cow"))))
      } yield result
      ScanamoAsync.exec(client)(farmerOps).futureValue should equal(
        Some(Right(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))))
    }
  }

  it("should return None when putting a new item asynchronously") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
    implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

    val farmersTable = Table[Farmer]("nursery-farmers")

    LocalDynamoDB.usingTable(client)("nursery-farmers")('name -> S) {
      val farmerOps = for {
        result <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
      } yield result
      ScanamoAsync.exec(client)(farmerOps).futureValue should equal(None)
    }
  }

  it("conditionally put asynchronously") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
    implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

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

  it("conditionally put asynchronously with 'between' condition") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    implicit val formatFarm: DynamoFormat[Farm] = DerivedDynamoFormat.derive
    implicit val formatFarmer: DynamoFormat[Farmer] = DerivedDynamoFormat.derive

    import com.gu.scanamo.syntax._

    val farmersTable = Table[Farmer]("nursery-farmers")

    LocalDynamoDB.usingTable(client)("nursery-farmers")('name -> S) {
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 55, Farm(List("sheep", "cow"))))
        _ <- farmersTable.put(Farmer("Butch", 57, Farm(List("cattle"))))
        _ <- farmersTable.put(Farmer("Wade", 58, Farm(List("chicken", "sheep"))))
        _ <- farmersTable.given('age between (56 and 57)).put(Farmer("Butch", 57, Farm(List("chicken"))))
        _ <- farmersTable.given('age between (58 and 59)).put(Farmer("Butch", 57, Farm(List("dinosaur"))))
        farmerButch <- farmersTable.get('name -> "Butch")
      } yield farmerButch
      ScanamoAsync.exec(client)(farmerOps).futureValue should equal(
        Some(Right(Farmer("Butch", 57, Farm(List("chicken")))))
      )
    }
  }

  it("conditionally delete asynchronously") {
    case class Gremlin(number: Int, wet: Boolean)

    implicit val format: DynamoFormat[Gremlin] = DerivedDynamoFormat.derive

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
