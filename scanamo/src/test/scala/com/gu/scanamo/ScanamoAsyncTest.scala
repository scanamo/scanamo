package com.gu.scanamo

import cats.implicits._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.query._
import com.gu.scanamo.syntax._

class ScanamoAsyncTest extends FunSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(15, Millis))

  val client = LocalDynamoDB.client()
  import scala.concurrent.ExecutionContext.Implicits.global

  override protected def afterAll(): Unit = {
    client.shutdown()
    super.afterAll()
  }

  it("should put asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      val farmers = Table[Farmer](t)

      val result = for {
        _ <- farmers.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        f <- farmers.get('name -> "McDonald")
      } yield f

      ScanamoAsync.exec(client)(result).futureValue should equal(
        Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "cow")))))
      )
    }
  }

  it("should get asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      val farmers = Table[Farmer](t)

      val result = for {
        _ <- farmers.put(Farmer("Maggot", 75L, Farm(List("dog"))))
        r1 <- farmers.get(UniqueKey(KeyEquals('name, "Maggot")))
        r2 <- farmers.get('name -> "Maggot")
      } yield (r1, r1 == r2)

      ScanamoAsync.exec(client)(result).futureValue should equal(
        (Some(Right(Farmer("Maggot", 75, Farm(List("dog"))))), true)
      )
    }

    LocalDynamoDB.usingRandomTable(client)('name -> S, 'number -> N) { t =>
      case class Engine(name: String, number: Int)

      val engines = Table[Engine](t)

      val result = for {
        _ <- engines.put(Engine("Thomas", 1))
        e <- engines.get('name -> "Thomas" and 'number -> 1)
      } yield e

      ScanamoAsync.exec(client)(result).futureValue should equal(Some(Right(Engine("Thomas", 1))))
    }
  }

  it("should get consistently asynchronously") {
    case class City(name: String, country: String)
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val cities = Table[City](t)

      val result = for {
        _ <- cities.put(City("Nashville", "US"))
        c <- cities.consistently.get('name -> "Nashville")
      } yield c

      ScanamoAsync.exec(client)(result).futureValue should equal(Some(Right(City("Nashville", "US"))))
    }
  }

  it("should delete asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      val farmers = Table[Farmer](t)

      ScanamoAsync
        .exec(client) {
          for {
            _ <- farmers.put(Farmer("McGregor", 62L, Farm(List("rabbit"))))
            _ <- farmers.delete('name -> "McGregor")
            f <- farmers.get('name -> "McGregor")
          } yield f
        }
        .futureValue should equal(None)
    }
  }

  it("should deleteAll asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      val farmers = Table[Farmer](t)

      val dataSet = Set(
        Farmer("Patty", 200L, Farm(List("unicorn"))),
        Farmer("Ted", 40L, Farm(List("T-Rex"))),
        Farmer("Jack", 2L, Farm(List("velociraptor")))
      )

      val ops = for {
        _ <- farmers.putAll(dataSet)
        _ <- farmers.deleteAll('name -> dataSet.map(_.name))
        fs <- farmers.scan
      } yield fs

      ScanamoAsync.exec(client)(ops).futureValue should equal(List.empty)
    }
  }

  it("should update asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('location -> S) { t =>
      case class Forecast(location: String, weather: String)

      val forecasts = Table[Forecast](t)
      val ops = for {
        _ <- forecasts.put(Forecast("London", "Rain"))
        _ <- forecasts.update('location -> "London", set('weather -> "Sun"))
        fs <- forecasts.scan
      } yield fs

      ScanamoAsync.exec(client)(ops).futureValue should equal(List(Right(Forecast("London", "Sun"))))
    }
  }

  it("should update asynchronously if a condition holds") {
    LocalDynamoDB.usingRandomTable(client)('location -> S) { t =>
      case class Forecast(location: String, weather: String, equipment: Option[String])

      val forecasts = Table[Forecast](t)

      val ops = for {
        _ <- forecasts.putAll(Set(Forecast("London", "Rain", None), Forecast("Birmingham", "Sun", None)))
        _ <- forecasts.given('weather -> "Rain").update('location -> "London", set('equipment -> Some("umbrella")))
        _ <- forecasts.given('weather -> "Rain").update('location -> "Birmingham", set('equipment -> Some("umbrella")))
        results <- forecasts.scan()
      } yield results

      ScanamoAsync.exec(client)(ops).futureValue should equal(
        List(Right(Forecast("London", "Rain", Some("umbrella"))), Right(Forecast("Birmingham", "Sun", None)))
      )
    }
  }

  it("should scan asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Bear(name: String, favouriteFood: String)

      val bears = Table[Bear](t)

      val ops = for {
        _ <- bears.put(Bear("Pooh", "honey"))
        _ <- bears.put(Bear("Yogi", "picnic baskets"))
        bs <- bears.scan
      } yield bs

      ScanamoAsync.exec(client)(ops).futureValue should equal(
        List(Right(Bear("Pooh", "honey")), Right(Bear("Yogi", "picnic baskets")))
      )
    }

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Lemming(name: String, stuff: String)
      val lemmings = Table[Lemming](t)
      val ops = for {
        _ <- lemmings.putAll(List.fill(100)(Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toSet)
        ls <- lemmings.scan
      } yield ls

      ScanamoAsync.exec(client)(ops).futureValue.size should equal(100)
    }
  }

  it("scans with a limit asynchronously") {
    case class Bear(name: String, favouriteFood: String)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val bears = Table[Bear](t)
      val ops = for {
        _ <- bears.put(Bear("Pooh", "honey"))
        _ <- bears.put(Bear("Yogi", "picnic baskets"))
        bs <- bears.limit(1).scan
      } yield bs
      ScanamoAsync.exec(client)(ops).futureValue should equal(List(Right(Bear("Pooh", "honey"))))
    }
  }

  it("scanIndexWithLimit") {
    case class Bear(name: String, favouriteFood: String, alias: Option[String])

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('name -> S)('alias -> S) { (t, i) =>
      val bears = Table[Bear](t)
      val ops = for {
        _ <- bears.put(Bear("Pooh", "honey", Some("Winnie")))
        _ <- bears.put(Bear("Yogi", "picnic baskets", None))
        _ <- bears.put(Bear("Graham", "quinoa", Some("Guardianista")))
        bs <- bears.index(i).limit(1).scan
      } yield bs
      ScanamoAsync.exec(client)(ops).futureValue should equal(
        List(Right(Bear("Graham", "quinoa", Some("Guardianista"))))
      )
    }
  }

  // it("Paginate scanIndexWithLimit") {
  //   case class Bear(name: String, favouriteFood: String, alias: Option[String])

  //   LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('name -> S)('alias -> S) { (t, i) =>
  //     val bears = Table[Bear](t)
  //     val ops = for {
  //       _ <- bears.put(Bear("Pooh", "honey", Some("Winnie")))
  //       _ <- bears.put(Bear("Yogi", "picnic baskets", Some("Kanga")))
  //       _ <- bears.put(Bear("Graham", "quinoa", Some("Guardianista")))
  //       bs <- for {
  //         res1 <- bears.index(i).limit(1).scanFrom(None)
  //         res2 <- bears.index(i).limit(1).scanFrom(res1._2)
  //         res3 <- bears.index(i).limit(1).scanFrom(res2._2)
  //       } yield res2._1 ::: res3._1
  //     } yield bs

  //     ScanamoAsync.exec(client)(ops).futureValue should equal(
  //       List(Right(Bear("Yogi", "picnic baskets", Some("Kanga"))), Right(Bear("Pooh", "honey", Some("Winnie"))))
  //     )
  //   }
  // }

  it("should query asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('species -> S, 'number -> N) { t =>
      case class Animal(species: String, number: Int)
      val animals = Table[Animal](t)
      val ops = for {
        _ <- animals.put(Animal("Wolf", 1))
        _ <- (1 to 3).toList.traverse(i => animals.put(Animal("Pig", i)))
        r1 <- animals.query('species -> "Pig")
        r2 <- animals.query('species -> "Pig" and 'number < 3)
        r3 <- animals.query('species -> "Pig" and 'number > 1)
        r4 <- animals.query('species -> "Pig" and 'number <= 2)
        r5 <- animals.query('species -> "Pig" and 'number >= 2)
      } yield (r1, r2, r3, r4, r5)

      ScanamoAsync.exec(client)(ops).futureValue should equal(
        (
          List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2)), Right(Animal("Pig", 3))),
          List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2))),
          List(Right(Animal("Pig", 2)), Right(Animal("Pig", 3))),
          List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2))),
          List(Right(Animal("Pig", 2)), Right(Animal("Pig", 3)))
        )
      )
    }

    LocalDynamoDB.usingRandomTable(client)('mode -> S, 'line -> S) { t =>
      case class Transport(mode: String, line: String)
      val transports = Table[Transport](t)
      val ops = for {
        _ <- transports.putAll(
          Set(
            Transport("Underground", "Circle"),
            Transport("Underground", "Metropolitan"),
            Transport("Underground", "Central")
          )
        )
        ts <- transports.query('mode -> "Underground" and ('line beginsWith "C"))
      } yield ts

      ScanamoAsync.exec(client)(ops).futureValue should equal(
        List(Right(Transport("Underground", "Central")), Right(Transport("Underground", "Circle")))
      )
    }
  }

  it("queries with a limit asynchronously") {
    case class Transport(mode: String, line: String)

    LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
      val transports = Table[Transport](t)
      val result = for {
        _ <- transports.putAll(
          Set(
            Transport("Underground", "Circle"),
            Transport("Underground", "Metropolitan"),
            Transport("Underground", "Central")
          )
        )
        rs <- transports.limit(1).query('mode -> "Underground" and ('line beginsWith "C"))
      } yield rs

      ScanamoAsync.exec(client)(result).futureValue should equal(List(Right(Transport("Underground", "Central"))))
    }
  }

  it("queries an index with a limit asynchronously") {
    case class Transport(mode: String, line: String, colour: String)

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('mode -> S, 'line -> S)('mode -> S, 'colour -> S) {
      (t, i) =>
        val transports = Table[Transport](t)
        val result = for {
          _ <- transports.putAll(
            Set(
              Transport("Underground", "Circle", "Yellow"),
              Transport("Underground", "Metropolitan", "Magenta"),
              Transport("Underground", "Central", "Red"),
              Transport("Underground", "Picadilly", "Blue"),
              Transport("Underground", "Northern", "Black")
            )
          )
          rs <- transports
            .index(i)
            .limit(1)
            .query(
              'mode -> "Underground" and ('colour beginsWith "Bl")
            )
        } yield rs

        ScanamoAsync.exec(client)(result).futureValue should equal(
          List(Right(Transport("Underground", "Northern", "Black")))
        )
    }
  }

  it("queries an index asynchronously with 'between' sort-key condition") {
    case class Station(mode: String, name: String, zone: Int)

    def deletaAllStations(stationTable: Table[Station], stations: Set[Station]) =
      stationTable.deleteAll(
        UniqueKeys(MultipleKeyList(('mode, 'name), stations.map(station => (station.mode, station.name))))
      )

    val LiverpoolStreet = Station("Underground", "Liverpool Street", 1)
    val CamdenTown = Station("Underground", "Camden Town", 2)
    val GoldersGreen = Station("Underground", "Golders Green", 3)
    val Hainault = Station("Underground", "Hainault", 4)

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('mode -> S, 'name -> S)('mode -> S, 'zone -> N) { (t, i) =>
      val stationTable = Table[Station](t)
      val stations = Set(LiverpoolStreet, CamdenTown, GoldersGreen, Hainault)
      val ops = for {
        _ <- stationTable.putAll(stations)
        ts1 <- stationTable.index(i).query('mode -> "Underground" and ('zone between (2 and 4)))
        ts2 <- for { _ <- deletaAllStations(stationTable, stations); ts <- stationTable.scan } yield ts
        _ <- stationTable.putAll(Set(LiverpoolStreet))
        ts3 <- stationTable.index(i).query('mode -> "Underground" and ('zone between (2 and 4)))
        ts4 <- for { _ <- deletaAllStations(stationTable, stations); ts <- stationTable.scan } yield ts
        _ <- stationTable.putAll(Set(CamdenTown))
        ts5 <- stationTable.index(i).query('mode -> "Underground" and ('zone between (1 and 1)))
      } yield (ts1, ts2, ts3, ts4, ts5)

      ScanamoAsync.exec(client)(ops).futureValue should equal(
        (
          List(Right(CamdenTown), Right(GoldersGreen), Right(Hainault)),
          List.empty,
          List.empty,
          List.empty,
          List.empty
        )
      )
    }
  }

  it("queries for items that are missing an attribute") {
    case class Farmer(firstName: String, surname: String, age: Option[Int])

    LocalDynamoDB.usingRandomTable(client)('firstName -> S, 'surname -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("Fred", "Perry", None))
        _ <- farmersTable.put(Farmer("Fred", "McDonald", Some(54)))
        farmerWithNoAge <- farmersTable.filter(attributeNotExists('age)).query('firstName -> "Fred")
      } yield farmerWithNoAge
      ScanamoAsync.exec(client)(farmerOps).futureValue should equal(
        List(Right(Farmer("Fred", "Perry", None)))
      )
    }
  }

  it("should put multiple items asynchronously") {
    case class Rabbit(name: String)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val rabbits = Table[Rabbit](t)
      val result = for {
        _ <- rabbits.putAll(List.fill(100)(Rabbit(util.Random.nextString(500))).toSet)
        rs <- rabbits.scan
      } yield rs

      ScanamoAsync.exec(client)(result).futureValue.size should equal(100)
    }
  }

  it("should get multiple items asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Farm(animals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)
      val farmers = Table[Farmer](t)

      ScanamoAsync
        .exec(client)(for {
          _ <- farmers.putAll(
            Set(
              Farmer("Boggis", 43L, Farm(List("chicken"))),
              Farmer("Bunce", 52L, Farm(List("goose"))),
              Farmer("Bean", 55L, Farm(List("turkey")))
            )
          )
          fs1 <- farmers.getAll(UniqueKeys(KeyList('name, Set("Boggis", "Bean"))))
          fs2 <- farmers.getAll('name -> Set("Boggis", "Bean"))
        } yield (fs1, fs2))
        .futureValue should equal(
        (
          Set(Right(Farmer("Boggis", 43, Farm(List("chicken")))), Right(Farmer("Bean", 55, Farm(List("turkey"))))),
          Set(Right(Farmer("Boggis", 43, Farm(List("chicken")))), Right(Farmer("Bean", 55, Farm(List("turkey")))))
        )
      )
    }

    LocalDynamoDB.usingRandomTable(client)('actor -> S, 'regeneration -> N) { t =>
      case class Doctor(actor: String, regeneration: Int)
      val doctors = Table[Doctor](t)

      ScanamoAsync
        .exec(client)(for {
          _ <- doctors.putAll(Set(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))
          ds <- doctors.getAll(('actor and 'regeneration) -> Set("McCoy" -> 9, "Ecclestone" -> 11))
        } yield ds)
        .futureValue should equal(Set(Right(Doctor("McCoy", 9)), Right(Doctor("Ecclestone", 11))))
    }
  }

  it("should get multiple items asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingRandomTable(client)('id -> N) { t =>
      case class Farm(id: Int, name: String)
      val farms = (1 to 101).map(i => Farm(i, s"Farm #$i")).toSet
      val farmsTable = Table[Farm](t)

      ScanamoAsync
        .exec(client)(for {
          _ <- farmsTable.putAll(farms)
          fs <- farmsTable.getAll(UniqueKeys(KeyList('id, farms.map(_.id))))
        } yield fs)
        .futureValue should equal(farms.map(Right(_)))
    }
  }

  it("should get multiple items consistently asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingRandomTable(client)('id -> N) { t =>
      case class Farm(id: Int, name: String)
      val farms = (1 to 101).map(i => Farm(i, s"Farm #$i")).toSet
      val farmsTable = Table[Farm](t)

      ScanamoAsync
        .exec(client)(for {
          _ <- farmsTable.putAll(farms)
          fs <- farmsTable.consistently.getAll(UniqueKeys(KeyList('id, farms.map(_.id))))
        } yield fs)
        .futureValue should equal(farms.map(Right(_)))
    }
  }

  it("should return old item after put asynchronously") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        result <- farmersTable.put(Farmer("McDonald", 50L, Farm(List("chicken", "cow"))))
      } yield result

      ScanamoAsync.exec(client)(farmerOps).futureValue should equal(
        Some(Right(Farmer("McDonald", 156L, Farm(List("sheep", "cow")))))
      )
    }
  }

  it("should return None when putting a new item asynchronously") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        result <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
      } yield result

      ScanamoAsync.exec(client)(farmerOps).futureValue should equal(
        None
      )
    }
  }

  it("conditionally put asynchronously") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val farmersTable = Table[Farmer](t)

      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        _ <- farmersTable.given('age -> 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"))))
        _ <- farmersTable.given('age -> 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"))))
        farmerWithNewStock <- farmersTable.get('name -> "McDonald")
      } yield farmerWithNewStock

      ScanamoAsync.exec(client)(farmerOps).futureValue should equal(
        Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "chicken")))))
      )
    }
  }

  it("conditionally put asynchronously with 'between' condition") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val farmersTable = Table[Farmer](t)

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

    LocalDynamoDB.usingRandomTable(client)('number -> N) { t =>
      val gremlinsTable = Table[Gremlin](t)

      val ops = for {
        _ <- gremlinsTable.putAll(Set(Gremlin(1, false), Gremlin(2, true)))
        _ <- gremlinsTable.given('wet -> true).delete('number -> 1)
        _ <- gremlinsTable.given('wet -> true).delete('number -> 2)
        remainingGremlins <- gremlinsTable.scan()
      } yield remainingGremlins

      ScanamoAsync.exec(client)(ops).futureValue should equal(
        List(Right(Gremlin(1, false)))
      )
    }
  }
}
