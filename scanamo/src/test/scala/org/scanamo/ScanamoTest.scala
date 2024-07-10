package org.scanamo

import cats.implicits.*
import org.scalatest.NonImplicitAssertions
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.fixtures.*
import org.scanamo.generic.auto.*
import org.scanamo.ops.ScanamoOps
import org.scanamo.query.*
import org.scanamo.syntax.*
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.*
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException

class ScanamoTest extends AnyFunSpec with Matchers with NonImplicitAssertions {
  val client = LocalDynamoDB.syncClient()
  val scanamo = Scanamo(client)

  it("should put asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      val result = for {
        _ <- farmers.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        f <- farmers.get("name" === "McDonald")
      } yield f

      scanamo.exec(result) should equal(
        Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "cow")))))
      )
    }
  }

  it("should get asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      val result = for {
        _ <- farmers.put(Farmer("Maggot", 75L, Farm(List("dog"))))
        r1 <- farmers.get(UniqueKey(KeyEquals("name", "Maggot")))
        r2 <- farmers.get("name" === "Maggot")
      } yield (r1, r1 == r2)

      scanamo.exec(result) should equal(
        (Some(Right(Farmer("Maggot", 75, Farm(List("dog"))))), true)
      )
    }

    LocalDynamoDB.usingRandomTable(client)("name" -> S, "number" -> N) { t =>
      val engines = Table[Engine](t)

      val result = for {
        _ <- engines.put(Engine("Thomas", 1))
        e <- engines.get("name" === "Thomas" and "number" === 1)
      } yield e

      scanamo.exec(result) should equal(Some(Right(Engine("Thomas", 1))))
    }
  }

  it("should get consistently asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val cities = Table[City](t)

      val result = for {
        _ <- cities.put(City("Nashville", "US"))
        c <- cities.consistently.get("name" === "Nashville")
      } yield c

      scanamo.exec(result) should equal(Some(Right(City("Nashville", "US"))))
    }
  }

  it("should delete asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      scanamo.exec {
        for {
          _ <- farmers.put(Farmer("McGregor", 62L, Farm(List("rabbit"))))
          _ <- farmers.delete("name" === "McGregor")
          f <- farmers.get("name" === "McGregor")
        } yield f
      } should equal(None)
    }
  }

  it("should deleteAll asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      val dataSet = Set(
        Farmer("Patty", 200L, Farm(List("unicorn"))),
        Farmer("Ted", 40L, Farm(List("T-Rex"))),
        Farmer("Jack", 2L, Farm(List("velociraptor")))
      )

      val ops = for {
        _ <- farmers.putAll(dataSet)
        _ <- farmers.deleteAll("name" in dataSet.map(_.name))
        fs <- farmers.scan()
      } yield fs

      scanamo.exec(ops) should equal(List.empty)
    }
  }

  it("should update asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("location" -> S) { t =>
      val forecasts = Table[Forecast](t)
      val ops = for {
        _ <- forecasts.put(Forecast("London", "Rain", None))
        _ <- forecasts.update("location" === "London", set("weather", "Sun"))
        fs <- forecasts.scan()
      } yield fs

      scanamo.exec(ops) should equal(List(Right(Forecast("London", "Sun", None))))
    }
  }

  it("should update asynchronously if a condition holds") {
    LocalDynamoDB.usingRandomTable(client)("location" -> S) { t =>
      val forecasts = Table[Forecast](t)

      val ops = for {
        _ <- forecasts.putAll(Set(Forecast("London", "Rain", None), Forecast("Birmingham", "Sun", None)))
        _ <- forecasts.when("weather" === "Rain").update("location" === "London", set("equipment", Some("umbrella")))
        _ <-
          forecasts
            .when("weather" === "Rain")
            .update("location" === "Birmingham", set("equipment", Some("umbrella")))
        results <- forecasts.scan()
      } yield results

      scanamo.exec(ops) should equal(
        List(Right(Forecast("London", "Rain", Some("umbrella"))), Right(Forecast("Birmingham", "Sun", None)))
      )
    }
  }

  it("should scan asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val bears = Table[Bear](t)

      val ops = for {
        _ <- bears.put(Bear("Pooh", "honey", None))
        _ <- bears.put(Bear("Yogi", "picnic baskets", None))
        bs <- bears.scan()
      } yield bs

      scanamo.exec(ops) should equal(
        List(Right(Bear("Pooh", "honey", None)), Right(Bear("Yogi", "picnic baskets", None)))
      )
    }

    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val lemmings = Table[Lemming](t)
      val ops = for {
        _ <- lemmings.putAll(List.fill(100)(Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toSet)
        ls <- lemmings.scan()
      } yield ls

      scanamo.exec(ops).size should equal(100)
    }
  }

  it("scans with a limit asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val bears = Table[Bear](t)
      val ops = for {
        _ <- bears.put(Bear("Pooh", "honey", None))
        _ <- bears.put(Bear("Yogi", "picnic baskets", None))
        bs <- bears.limit(1).scan()
      } yield bs
      scanamo.exec(ops) should equal(List(Right(Bear("Pooh", "honey", None))))
    }
  }

  it("scanIndexWithLimit") {
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("name" -> S)("alias" -> S) { (t, i) =>
      val bears = Table[Bear](t)
      val ops = for {
        _ <- bears.put(Bear("Pooh", "honey", Some("Winnie")))
        _ <- bears.put(Bear("Yogi", "picnic baskets", None))
        _ <- bears.put(Bear("Graham", "quinoa", Some("Guardianista")))
        bs <- bears.index(i).limit(1).scan()
      } yield bs
      scanamo.exec(ops) should equal(List(Right(Bear("Graham", "quinoa", Some("Guardianista")))))
    }
  }

  it("Paginate scanIndexWithLimit") {
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("name" -> S)("alias" -> S) { (t, i) =>
      val bears = Table[Bear](t)
      val ops = for {
        _ <- bears.put(Bear("Pooh", "honey", Some("Winnie")))
        _ <- bears.put(Bear("Yogi", "picnic baskets", Some("Kanga")))
        _ <- bears.put(Bear("Graham", "quinoa", Some("Guardianista")))
        bs <- for {
          _ <- bears.index(i).limit(1).scan()
          res2 <- bears.index(i).limit(1).from("name" === "Graham" and "alias" === "Guardianista").scan()
          res3 <- bears.index(i).limit(1).from("name" === "Yogi" and "alias" === "Kanga").scan()
        } yield res2 ::: res3
      } yield bs

      scanamo.exec(ops) should equal(
        List(Right(Bear("Yogi", "picnic baskets", Some("Kanga"))), Right(Bear("Pooh", "honey", Some("Winnie"))))
      )
    }
  }

  it("should stream full table scan") {
    import cats.{ Id, ~> }
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val list = List(
        Item("item #1"),
        Item("item #2"),
        Item("item #3"),
        Item("item #4"),
        Item("item #5"),
        Item("item #6")
      )
      val expected = list.map(i => List(Right(i)))

      val items = Table[Item](t)
      val ops = for {
        _ <- items.putAll(list.toSet).toFreeT[Stream]
        list <- items.scanPaginatedM[Stream](1)
      } yield list

      val f = new (Id ~> Stream) {
        override def apply[A](a: Id[A]): Stream[A] = Stream(a)
      }

      scanamo.execT(f)(ops) should contain theSameElementsAs expected
    }
  }

  it("should query asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("species" -> S, "number" -> N) { t =>
      val animals = Table[Animal](t)
      val ops = for {
        _ <- animals.put(Animal("Wolf", 1))
        _ <- (1 to 3).toList.traverse(i => animals.put(Animal("Pig", i)))
        r1 <- animals.query("species" === "Pig")
        r2 <- animals.query("species" === "Pig" and "number" < 3)
        r3 <- animals.query("species" === "Pig" and "number" > 1)
        r4 <- animals.query("species" === "Pig" and "number" <= 2)
        r5 <- animals.query("species" === "Pig" and "number" >= 2)
      } yield (r1, r2, r3, r4, r5)

      scanamo.exec(ops) should equal(
        (
          List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2)), Right(Animal("Pig", 3))),
          List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2))),
          List(Right(Animal("Pig", 2)), Right(Animal("Pig", 3))),
          List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2))),
          List(Right(Animal("Pig", 2)), Right(Animal("Pig", 3)))
        )
      )
    }

    LocalDynamoDB.usingRandomTable(client)("mode" -> S, "line" -> S) { t =>
      val transports = Table[Transport](t)
      val ops = for {
        _ <- transports.putAll(
          Set(
            Transport("Underground", "Circle", "Yellow"),
            Transport("Underground", "Metropolitan", "Purple"),
            Transport("Underground", "Central", "Red")
          )
        )
        ts <- transports.query("mode" === "Underground" and ("line" beginsWith "C"))
      } yield ts

      scanamo.exec(ops) should equal(
        List(Right(Transport("Underground", "Central", "Red")), Right(Transport("Underground", "Circle", "Yellow")))
      )
    }
  }

  it("queries with a limit asynchronously") {
    LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
      val transports = Table[Transport](t)
      val result = for {
        _ <- transports.putAll(
          Set(
            Transport("Underground", "Circle", "Yellow"),
            Transport("Underground", "Metropolitan", "Purple"),
            Transport("Underground", "Central", "Red")
          )
        )
        rs <- transports.limit(1).query("mode" === "Underground" and ("line" beginsWith "C"))
      } yield rs

      scanamo.exec(result) should equal(List(Right(Transport("Underground", "Central", "Red"))))
    }
  }

  it("queries an index with a limit asynchronously") {
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("mode" -> S, "line" -> S)("mode" -> S, "colour" -> S) {
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
          rs <-
            transports
              .index(i)
              .limit(1)
              .query(
                "mode" === "Underground" and ("colour" beginsWith "Bl")
              )
        } yield rs

        scanamo.exec(result) should equal(
          List(Right(Transport("Underground", "Northern", "Black")))
        )
    }
  }

  it("queries an index asynchronously with `between` sort-key condition") {
    def deletaAllStations(stationTable: Table[Station], stations: Set[Station]) =
      stationTable.deleteAll(
        UniqueKeys(MultipleKeyList(("line", "name"), stations.map(station => (station.line, station.name))))
      )

    val LiverpoolStreet = Station("Underground", "Liverpool Street", 1)
    val CamdenTown = Station("Underground", "Camden Town", 2)
    val GoldersGreen = Station("Underground", "Golders Green", 3)
    val Hainault = Station("Underground", "Hainault", 4)

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("line" -> S, "name" -> S)("line" -> S, "zone" -> N) {
      (t, i) =>
        val stationTable = Table[Station](t)
        val stations = Set(LiverpoolStreet, CamdenTown, GoldersGreen, Hainault)
        val ops = for {
          _ <- stationTable.putAll(stations)
          ts1 <- stationTable.index(i).query("line" === "Underground" and ("zone" between 2 and 4))
          ts2 <- for { _ <- deletaAllStations(stationTable, stations); ts <- stationTable.scan() } yield ts
          _ <- stationTable.putAll(Set(LiverpoolStreet))
          ts3 <- stationTable.index(i).query("line" === "Underground" and ("zone" between 2 and 4))
          ts4 <- for { _ <- deletaAllStations(stationTable, stations); ts <- stationTable.scan() } yield ts
          _ <- stationTable.putAll(Set(CamdenTown))
          ts5 <- stationTable.index(i).query("line" === "Underground" and ("zone" between 1 and 1))
        } yield (ts1, ts2, ts3, ts4, ts5)

        scanamo.exec(ops) should equal(
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
    LocalDynamoDB.usingRandomTable(client)("firstName" -> S, "surname" -> S) { t =>
      val farmersTable = Table[Worker](t)
      val farmerOps = for {
        _ <- farmersTable.put(Worker("Fred", "Perry", None))
        _ <- farmersTable.put(Worker("Fred", "McDonald", Some(54)))
        farmerWithNoAge <- farmersTable.filter(attributeNotExists("age")).query("firstName" === "Fred")
      } yield farmerWithNoAge
      scanamo.exec(farmerOps) should equal(
        List(Right(Worker("Fred", "Perry", None)))
      )
    }
  }

  it("should put multiple items asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val rabbits = Table[Rabbit](t)
      val result = for {
        _ <- rabbits.putAll(List.fill(100)(Rabbit(util.Random.nextString(500))).toSet)
        rs <- rabbits.scan()
      } yield rs

      scanamo.exec(result).size should equal(100)
    }
  }

  it("should get multiple items asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      scanamo.exec(for {
        _ <- farmers.putAll(
          Set(
            Farmer("Boggis", 43L, Farm(List("chicken"))),
            Farmer("Bunce", 52L, Farm(List("goose"))),
            Farmer("Bean", 55L, Farm(List("turkey")))
          )
        )
        fs1 <- farmers.getAll(UniqueKeys(KeyList("name", Set("Boggis", "Bean"))))
        fs2 <- farmers.getAll("name" in Set("Boggis", "Bean"))
      } yield (fs1, fs2)) should equal(
        (
          Set(Right(Farmer("Boggis", 43, Farm(List("chicken")))), Right(Farmer("Bean", 55, Farm(List("turkey"))))),
          Set(Right(Farmer("Boggis", 43, Farm(List("chicken")))), Right(Farmer("Bean", 55, Farm(List("turkey")))))
        )
      )
    }

    LocalDynamoDB.usingRandomTable(client)("actor" -> S, "regeneration" -> N) { t =>
      val doctors = Table[Doctor](t)

      scanamo.exec(for {
        _ <- doctors.putAll(Set(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))
        ds <- doctors.getAll("actor" -> "regeneration" =*= Set("McCoy" -> 9, "Ecclestone" -> 11))
      } yield ds) should equal(Set(Right(Doctor("McCoy", 9)), Right(Doctor("Ecclestone", 11))))
    }
  }

  it("should get multiple items asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingRandomTable(client)("id" -> N) { t =>
      val farms = (1 to 101).map(i => Factory(i, s"Farm #$i")).toSet
      val farmsTable = Table[Factory](t)

      scanamo.exec(for {
        _ <- farmsTable.putAll(farms)
        fs <- farmsTable.getAll(UniqueKeys(KeyList("id", farms.map(_.id))))
      } yield fs) should equal(farms.map(Right(_)))
    }
  }

  it("should get multiple items consistently asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingRandomTable(client)("id" -> N) { t =>
      val farms = (1 to 101).map(i => Factory(i, s"Farm #$i")).toSet
      val farmsTable = Table[Factory](t)

      scanamo.exec(for {
        _ <- farmsTable.putAll(farms)
        fs <- farmsTable.consistently.getAll(UniqueKeys(KeyList("id", farms.map(_.id))))
      } yield fs) should equal(farms.map(Right(_)))
    }
  }

  it("should return old item after put asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        result <- farmersTable.putAndReturn(PutReturn.OldValue)(Farmer("McDonald", 50L, Farm(List("chicken", "cow"))))
      } yield result

      scanamo.exec(farmerOps) should equal(
        Some(Right(Farmer("McDonald", 156L, Farm(List("sheep", "cow")))))
      )
    }
  }

  it("should return None when putting a new item asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        result <- farmersTable.putAndReturn(PutReturn.OldValue)(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
      } yield result

      scanamo.exec(farmerOps) should equal(
        None
      )
    }
  }

  it("conditionally put asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)

      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        _ <- farmersTable.when("age" === 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"))))
        _ <- farmersTable.when("age" === 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"))))
        farmerWithNewStock <- farmersTable.get("name" === "McDonald")
      } yield farmerWithNewStock

      scanamo.exec(farmerOps) should equal(
        Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "chicken")))))
      )
    }
  }

  it("conditionally put asynchronously with `between` condition") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)

      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 55, Farm(List("sheep", "cow"))))
        _ <- farmersTable.put(Farmer("Butch", 57, Farm(List("cattle"))))
        _ <- farmersTable.put(Farmer("Wade", 58, Farm(List("chicken", "sheep"))))
        _ <- farmersTable.when("age" between 56 and 57).put(Farmer("Butch", 57, Farm(List("chicken"))))
        _ <- farmersTable.when("age" between 58 and 59).put(Farmer("Butch", 57, Farm(List("dinosaur"))))
        farmerButch <- farmersTable.get("name" === "Butch")
      } yield farmerButch
      scanamo.exec(farmerOps) should equal(
        Some(Right(Farmer("Butch", 57, Farm(List("chicken")))))
      )
    }
  }

  it("conditionally delete asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("number" -> N) { t =>
      val gremlinsTable = Table[Gremlin](t)

      val ops = for {
        _ <- gremlinsTable.putAll(Set(Gremlin(1, false), Gremlin(2, true)))
        _ <- gremlinsTable.when("wet" === true).delete("number" === 1)
        _ <- gremlinsTable.when("wet" === true).delete("number" === 2)
        remainingGremlins <- gremlinsTable.scan()
      } yield remainingGremlins

      scanamo.exec(ops) should equal(
        List(Right(Gremlin(1, false)))
      )
    }
  }

  it("should return None after delete asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        result <- farmersTable.deleteAndReturn(DeleteReturn.Nothing)("name" === "McDonald")
      } yield result

      scanamo.exec(farmerOps) should equal(
        None
      )
    }
  }

  it("should return old item after delete asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        result <- farmersTable.deleteAndReturn(DeleteReturn.OldValue)("name" === "McDonald")
      } yield result

      scanamo.exec(farmerOps) should equal(
        Some(Right(Farmer("McDonald", 156L, Farm(List("sheep", "cow")))))
      )
    }
  }

  it("transact table write (update) items") {
    LocalDynamoDB.usingRandomTable(client)("location" -> S) { t =>
      val forecastTable = Table[Forecast](t)

      val ops: ScanamoOps[List[Either[DynamoReadError, Forecast]]] = for {
        _ <- forecastTable.putAll(
          Set(Forecast("London", "Sun", None), Forecast("Amsterdam", "Fog", None), Forecast("Manchester", "Rain", None))
        )
        _ <- forecastTable.transactUpdateAll(
          List(
            UniqueKey(KeyEquals("location", "London")) -> set("weather", "Rain"),
            UniqueKey(KeyEquals("location", "Amsterdam")) -> set("weather", "Cloud")
          )
        )
        items <- forecastTable.scan()
      } yield items

      scanamo.exec(ops) should equal(
        List(
          Right(Forecast("Amsterdam", "Cloud", None)),
          Right(Forecast("London", "Rain", None)),
          Right(Forecast("Manchester", "Rain", None))
        )
      )
    }
  }

  it("transact write (update) items in multiple tables") {
    LocalDynamoDB.usingRandomTable(client)("number" -> N) { t1 =>
      LocalDynamoDB.usingRandomTable(client)("location" -> S) { t2 =>
        val gremlinTable = Table[Gremlin](t1)
        val forecastTable = Table[Forecast](t2)

        val ops = for {
          _ <- gremlinTable.putAll(Set(Gremlin(1, wet = false), Gremlin(2, wet = true)))
          _ <- forecastTable.putAll(Set(Forecast("London", "Sun", None), Forecast("Amsterdam", "Fog", None)))
          _ <- forecastTable.transactUpdateAll(
            List(
              UniqueKey(KeyEquals("location", "London")) -> set("weather", "Rain")
            )
          )
          _ <- gremlinTable.transactUpdateAll(
            List(
              UniqueKey(KeyEquals("number", 2)) -> set("wet", true)
            )
          )
          gremlins <- gremlinTable.scan()
          forecasts <- forecastTable.scan()
        } yield (gremlins, forecasts)

        scanamo.exec(ops) should equal(
          (
            List(Right(Gremlin(2, wet = true)), Right(Gremlin(1, wet = false))),
            List(Right(Forecast("Amsterdam", "Fog", None)), Right(Forecast("London", "Rain", None)))
          )
        )
      }
    }
  }

  it("transact table write (delete) items") {
    LocalDynamoDB.usingRandomTable(client)("location" -> S) { t =>
      val forecastTable = Table[Forecast](t)

      val ops: ScanamoOps[List[Either[DynamoReadError, Forecast]]] = for {
        _ <- forecastTable.putAll(
          Set(Forecast("London", "Sun", None), Forecast("Amsterdam", "Fog", None), Forecast("Manchester", "Rain", None))
        )
        _ <- forecastTable.transactDeleteAll(
          List(
            UniqueKey(KeyEquals("location", "London")),
            UniqueKey(KeyEquals("location", "Amsterdam"))
          )
        )
        items <- forecastTable.scan()
      } yield items

      scanamo.exec(ops) should equal(
        List(Right(Forecast("Manchester", "Rain", None)))
      )
    }
  }

  it("transact write (delete) items in multiple tables") {
    LocalDynamoDB.usingRandomTable(client)("number" -> N) { t1 =>
      LocalDynamoDB.usingRandomTable(client)("location" -> S) { t2 =>
        val gremlinTable = Table[Gremlin](t1)
        val forecastTable = Table[Forecast](t2)

        val ops = for {
          _ <- gremlinTable.putAll(Set(Gremlin(1, wet = false), Gremlin(2, wet = true)))
          _ <- forecastTable.putAll(Set(Forecast("London", "Sun", None), Forecast("Amsterdam", "Fog", None)))
          _ <- forecastTable.transactDeleteAll(
            List(
              UniqueKey(KeyEquals("location", "London"))
            )
          )
          _ <- gremlinTable.transactDeleteAll(
            List(
              UniqueKey(KeyEquals("number", 2))
            )
          )
          gremlins <- gremlinTable.scan()
          forecasts <- forecastTable.scan()
        } yield (gremlins, forecasts)

        scanamo.exec(ops) should equal(
          (List(Right(Gremlin(1, wet = false))), List(Right(Forecast("Amsterdam", "Fog", None))))
        )
      }
    }
  }

  it("transact multiple write actions (put, update, delete) across multiple tables") {
    LocalDynamoDB.usingRandomTable(client)("number" -> N) { t1 =>
      LocalDynamoDB.usingRandomTable(client)("location" -> S) { t2 =>
        val gremlinTable = Table[Gremlin](t1)
        val forecastTable = Table[Forecast](t2)

        val ops = for {
          _ <- gremlinTable.putAll(Set(Gremlin(1, wet = false), Gremlin(2, wet = true)))
          _ <- forecastTable.putAll(Set(Forecast("London", "Sun", None), Forecast("Amsterdam", "Fog", None)))
          _ <- ScanamoFree.transactionalWrite(
            List(
              TransactionalWriteAction
                .Put(t1, Gremlin(3, wet = true)),
              TransactionalWriteAction
                .Put(t2, Forecast("Berlin", "Wind", None)),
              TransactionalWriteAction.Update(t1, UniqueKey(KeyEquals("number", 2)), set("wet", false)),
              TransactionalWriteAction.Delete(t2, UniqueKey(KeyEquals("location", "Amsterdam")))
            )
          )
          gremlins <- gremlinTable.scan()
          forecasts <- forecastTable.scan()
        } yield (gremlins, forecasts)

        scanamo.exec(ops) should equal(
          (
            List(Right(Gremlin(2, wet = false)), Right(Gremlin(1, wet = false)), Right(Gremlin(3, wet = true))),
            List(Right(Forecast("London", "Sun", None)), Right(Forecast("Berlin", "Wind", None)))
          )
        )
      }
    }
  }

  it("transact multiple write actions with a condition check action (where the condition check is satisfied)") {
    LocalDynamoDB.usingRandomTable(client)("number" -> N) { t1 =>
      LocalDynamoDB.usingRandomTable(client)("location" -> S) { t2 =>
        val gremlinTable = Table[Gremlin](t1)
        val forecastTable = Table[Forecast](t2)

        val ops = for {
          _ <- gremlinTable.putAll(Set(Gremlin(1, wet = false)))
          _ <- forecastTable.putAll(Set(Forecast("London", "Sun", None)))
          _ <- ScanamoFree.transactionalWrite(
            List(
              TransactionalWriteAction
                .Put(t1, Gremlin(3, wet = true)),
              TransactionalWriteAction
                .Put(t2, Forecast("Berlin", "Wind", None)),
              TransactionalWriteAction.ConditionCheck(t1, UniqueKey(KeyEquals("number", 1)), "wet" === false)
            )
          )
          gremlins <- gremlinTable.scan()
          forecasts <- forecastTable.scan()
        } yield (gremlins, forecasts)

        scanamo.exec(ops) should equal(
          (
            List(Right(Gremlin(1, wet = false)), Right(Gremlin(3, wet = true))),
            List(Right(Forecast("London", "Sun", None)), Right(Forecast("Berlin", "Wind", None)))
          )
        )
      }
    }
  }

  it("cancel the transaction if the condition check action fails") {
    LocalDynamoDB.usingRandomTable(client)("number" -> N) { t1 =>
      LocalDynamoDB.usingRandomTable(client)("location" -> S) { t2 =>
        val gremlinTable = Table[Gremlin](t1)
        val forecastTable = Table[Forecast](t2)

        val ops1 = for {
          _ <- gremlinTable.putAll(Set(Gremlin(1, wet = false)))
          _ <- forecastTable.putAll(Set(Forecast("London", "Sun", None)))
          result <- ScanamoFree.transactionalWrite(
            List(
              TransactionalWriteAction
                .Put(t1, Gremlin(3, wet = true)),
              TransactionalWriteAction
                .Put(t2, Forecast("Berlin", "Wind", None)),
              TransactionalWriteAction.ConditionCheck(t1, UniqueKey(KeyEquals("number", 1)), "wet" === true)
            )
          )
        } yield result

        scanamo.exec(ops1) shouldBe a[Left[TransactionCanceledException,_]]


        val ops2 = for {
          gremlins <- gremlinTable.scan()
          forecasts <- forecastTable.scan()
        } yield (gremlins, forecasts)

        scanamo.exec(ops2) should equal(
          (List(Right(Gremlin(1, wet = false))), List(Right(Forecast("London", "Sun", None))))
        )
      }
    }
  }

  it("should not update field if it exists") {
    LocalDynamoDB.usingRandomTable(client)("location" -> S) { t =>
      val forecasts = Table[Forecast](t)
      val ops = for {
        _ <- forecasts.put(Forecast("London", "Rain", Some("umbrella")))
        _ <- forecasts.update("location" === "London", setIfNotExists("equipment", "shades"))
        fs <- forecasts.scan()
      } yield fs

      scanamo.exec(ops) should equal(List(Right(Forecast("London", "Rain", Some("umbrella")))))
    }
  }

  it("should update field if it does not exist") {
    LocalDynamoDB.usingRandomTable(client)("location" -> S) { t =>
      val forecasts = Table[Forecast](t)
      val ops = for {
        _ <- forecasts.put(Forecast("London", "Sun", None))
        _ <- forecasts.update("location" === "London", setIfNotExists("equipment", "shades"))
        fs <- forecasts.scan()
      } yield fs

      scanamo.exec(ops) should equal(List(Right(Forecast("London", "Sun", Some("shades")))))
    }
  }
}
