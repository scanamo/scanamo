package org.scanamo

import cats.implicits._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
import org.scanamo.query._
import org.scanamo.syntax._
import org.scanamo.fixtures._
import org.scanamo.generic.auto._
import org.scanamo.ops.ScanamoOps

class ScanamoAsyncTest extends AnyFunSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(15, Millis))
  import scala.concurrent.ExecutionContext.Implicits.global

  val client = LocalDynamoDB.client()
  val scanamo = ScanamoAsync(client)

  override protected def afterAll(): Unit = {
    client.close()
    super.afterAll()
  }

  it("should put asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      val result = for {
        _ <- farmers.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"),100)))
        f <- farmers.get(AttributeName.of("name") === "McDonald")
      } yield f

      scanamo.exec(result).futureValue should equal(
        Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "cow"),100))))
      )
    }
  }

  it("should get asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      val result = for {
        _ <- farmers.put(Farmer("Maggot", 75L, Farm(List("dog"),100)))
        r1 <- farmers.get(UniqueKey(KeyEquals("name", "Maggot")))
        r2 <- farmers.get(AttributeName.of("name") === "Maggot")
      } yield (r1, r1 == r2)

      scanamo.exec(result).futureValue should equal(
        (Some(Right(Farmer("Maggot", 75, Farm(List("dog"),100)))), true)
      )
    }

    LocalDynamoDB.usingRandomTable(client)("name" -> S, "number" -> N) { t =>
      val engines = Table[Engine](t)

      val result = for {
        _ <- engines.put(Engine("Thomas", 1))
        e <- engines.get(AttributeName.of("name") === "Thomas" and AttributeName.of("number") === 1)
      } yield e

      scanamo.exec(result).futureValue should equal(Some(Right(Engine("Thomas", 1))))
    }
  }

  it("should get consistently asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val cities = Table[City](t)

      val result = for {
        _ <- cities.put(City("Nashville", "US"))
        c <- cities.consistently.get(AttributeName.of("name") === "Nashville")
      } yield c

      scanamo.exec(result).futureValue should equal(Some(Right(City("Nashville", "US"))))
    }
  }

  it("should delete asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      scanamo.exec {
        for {
          _ <- farmers.put(Farmer("McGregor", 62L, Farm(List("rabbit"),100)))
          _ <- farmers.delete(AttributeName.of("name") === "McGregor")
          f <- farmers.get(AttributeName.of("name") === "McGregor")
        } yield f
      }.futureValue should equal(None)
    }
  }

  it("should deleteAll asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      val dataSet = Set(
        Farmer("Patty", 200L, Farm(List("unicorn"),100)),
        Farmer("Ted", 40L, Farm(List("T-Rex"),100)),
        Farmer("Jack", 2L, Farm(List("velociraptor"),100))
      )

      val ops = for {
        _ <- farmers.putAll(dataSet)
        _ <- farmers.deleteAll("name" in dataSet.map(_.name))
        fs <- farmers.scan()
      } yield fs

      scanamo.exec(ops).futureValue should equal(List.empty)
    }
  }

  it("should update asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("location" -> S) { t =>
      val forecasts = Table[Forecast](t)
      val ops = for {
        _ <- forecasts.put(Forecast("London", "Rain", None))
        _ <- forecasts.update(AttributeName.of("location") === "London", set("weather", "Sun"))
        fs <- forecasts.scan()
      } yield fs

      scanamo.exec(ops).futureValue should equal(List(Right(Forecast("London", "Sun", None))))
    }
  }

  it("should update asynchronously if a condition holds") {
    LocalDynamoDB.usingRandomTable(client)("location" -> S) { t =>
      val forecasts = Table[Forecast](t)

      val ops = for {
        _ <- forecasts.putAll(Set(Forecast("London", "Rain", None), Forecast("Birmingham", "Sun", None)))
        _ <- forecasts.when(AttributeName.of("weather") === "Rain").update(AttributeName.of("location") === "London", set("equipment", Some("umbrella")))
        _ <-
          forecasts
            .when(AttributeName.of("weather") === "Rain")
            .update(AttributeName.of("location") === "Birmingham", set("equipment", Some("umbrella")))
        results <- forecasts.scan()
      } yield results

      scanamo.exec(ops).futureValue should equal(
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

      scanamo.exec(ops).futureValue should equal(
        List(Right(Bear("Pooh", "honey", None)), Right(Bear("Yogi", "picnic baskets", None)))
      )
    }

    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val lemmings = Table[Lemming](t)
      val ops = for {
        _ <- lemmings.putAll(List.fill(100)(Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toSet)
        ls <- lemmings.scan()
      } yield ls

      scanamo.exec(ops).futureValue.size should equal(100)
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
      scanamo.exec(ops).futureValue should equal(List(Right(Bear("Pooh", "honey", None))))
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
      scanamo.exec(ops).futureValue should equal(
        List(Right(Bear("Graham", "quinoa", Some("Guardianista"))))
      )
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
          res2 <- bears.index(i).limit(1).from(AttributeName.of("name") === "Graham" and AttributeName.of("alias") === "Guardianista").scan()
          res3 <- bears.index(i).limit(1).from(AttributeName.of("name") === "Yogi" and AttributeName.of("alias") === "Kanga").scan()
        } yield res2 ::: res3
      } yield bs

      scanamo.exec(ops).futureValue should equal(
        List(Right(Bear("Yogi", "picnic baskets", Some("Kanga"))), Right(Bear("Pooh", "honey", Some("Winnie"))))
      )
    }
  }

  it("should stream full table scan") {
    import cats.{ ~>, Apply, Monad, MonoidK }
    import cats.instances.future._
    import scala.concurrent.Future

    type SFuture[A] = Future[Stream[A]]

    implicit val applicative: MonoidK[SFuture] with Monad[SFuture] = new MonoidK[SFuture] with Monad[SFuture] {
      def combineK[A](x: SFuture[A], y: SFuture[A]): SFuture[A] = Apply[Future].map2(x, y)(_ ++ _)

      def empty[A]: SFuture[A] = Future.successful(Stream.empty)

      def flatMap[A, B](fa: SFuture[A])(f: A => SFuture[B]): SFuture[B] =
        fa flatMap { as =>
          Future.traverse(as)(f)
        } map (_.flatten)

      def tailRecM[A, B](a: A)(f: A => SFuture[Either[A, B]]): SFuture[B] =
        f(a) flatMap { eas =>
          Future.traverse(eas) {
            case Left(a)  => tailRecM(a)(f)
            case Right(b) => Future.successful(Stream(b))
          } map (_.flatten)
        }

      def pure[A](x: A): SFuture[A] = Future.successful(Stream(x))
    }

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
        _ <- items.putAll(list.toSet).toFreeT[SFuture]
        list <- items.scanPaginatedM[SFuture](1)
      } yield list

      val f = new (Future ~> SFuture) {
        override def apply[A](a: Future[A]): SFuture[A] = a.map(Stream(_))
      }

      scanamo.execT(f)(ops).futureValue should contain theSameElementsAs expected
    }
  }

  it("should query asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("species" -> S, "number" -> N) { t =>
      val animals = Table[Animal](t)
      val ops = for {
        _ <- animals.put(Animal("Wolf", 1))
        _ <- (1 to 3).toList.traverse(i => animals.put(Animal("Pig", i)))
        r1 <- animals.query(AttributeName.of("species") === "Pig")
        r2 <- animals.query(AttributeName.of("species") === "Pig" and "number" < 3)
        r3 <- animals.query(AttributeName.of("species") === "Pig" and "number" > 1)
        r4 <- animals.query(AttributeName.of("species") === "Pig" and "number" <= 2)
        r5 <- animals.query(AttributeName.of("species") === "Pig" and "number" >= 2)
      } yield (r1, r2, r3, r4, r5)

      scanamo.exec(ops).futureValue should equal(
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
        ts <- transports.query(AttributeName.of("mode") === "Underground" and ("line" beginsWith "C"))
      } yield ts

      scanamo.exec(ops).futureValue should equal(
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
        rs <- transports.limit(1).query(AttributeName.of("mode") === "Underground" and ("line" beginsWith "C"))
      } yield rs

      scanamo.exec(result).futureValue should equal(List(Right(Transport("Underground", "Central", "Red"))))
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
                AttributeName.of("mode") === "Underground" and ("colour" beginsWith "Bl")
              )
        } yield rs

        scanamo.exec(result).futureValue should equal(
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
          ts1 <- stationTable.index(i).query(AttributeName.of("line") === "Underground" and ("zone" between 2 and 4))
          ts2 <- for { _ <- deletaAllStations(stationTable, stations); ts <- stationTable.scan() } yield ts
          _ <- stationTable.putAll(Set(LiverpoolStreet))
          ts3 <- stationTable.index(i).query(AttributeName.of("line") === "Underground" and ("zone" between 2 and 4))
          ts4 <- for { _ <- deletaAllStations(stationTable, stations); ts <- stationTable.scan() } yield ts
          _ <- stationTable.putAll(Set(CamdenTown))
          ts5 <- stationTable.index(i).query(AttributeName.of("line") === "Underground" and ("zone" between 1 and 1))
        } yield (ts1, ts2, ts3, ts4, ts5)

        scanamo.exec(ops).futureValue should equal(
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
        farmerWithNoAge <- farmersTable.filter(attributeNotExists("age")).query(AttributeName.of("firstName") === "Fred")
      } yield farmerWithNoAge
      scanamo.exec(farmerOps).futureValue should equal(
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

      scanamo.exec(result).futureValue.size should equal(100)
    }
  }

  it("should get multiple items asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmers = Table[Farmer](t)

      scanamo
        .exec(for {
          _ <- farmers.putAll(
            Set(
              Farmer("Boggis", 43L, Farm(List("chicken"),100)),
              Farmer("Bunce", 52L, Farm(List("goose"),100)),
              Farmer("Bean", 55L, Farm(List("turkey"),100))
            )
          )
          fs1 <- farmers.getAll(UniqueKeys(KeyList("name", Set("Boggis", "Bean"))))
          fs2 <- farmers.getAll("name" in Set("Boggis", "Bean"))
        } yield (fs1, fs2))
        .futureValue should equal(
        (
          Set(Right(Farmer("Boggis", 43, Farm(List("chicken"),100))), Right(Farmer("Bean", 55, Farm(List("turkey"),100)))),
          Set(Right(Farmer("Boggis", 43, Farm(List("chicken"),100))), Right(Farmer("Bean", 55, Farm(List("turkey"),100))))
        )
      )
    }

    LocalDynamoDB.usingRandomTable(client)("actor" -> S, "regeneration" -> N) { t =>
      val doctors = Table[Doctor](t)

      scanamo
        .exec(for {
          _ <- doctors.putAll(Set(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))
          ds <- doctors.getAll(("actor" -> "regeneration") =*= Set("McCoy" -> 9, "Ecclestone" -> 11))
        } yield ds)
        .futureValue should equal(Set(Right(Doctor("McCoy", 9)), Right(Doctor("Ecclestone", 11))))
    }
  }

  it("should get multiple items asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingRandomTable(client)("id" -> N) { t =>
      val farms = (1 to 101).map(i => Factory(i, s"Farm #$i")).toSet
      val farmsTable = Table[Factory](t)

      scanamo
        .exec(for {
          _ <- farmsTable.putAll(farms)
          fs <- farmsTable.getAll(UniqueKeys(KeyList("id", farms.map(_.id))))
        } yield fs)
        .futureValue should equal(farms.map(Right(_)))
    }
  }

  it("should get multiple items consistently asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingRandomTable(client)("id" -> N) { t =>
      val farms = (1 to 101).map(i => Factory(i, s"Farm #$i")).toSet
      val farmsTable = Table[Factory](t)

      scanamo
        .exec(for {
          _ <- farmsTable.putAll(farms)
          fs <- farmsTable.consistently.getAll(UniqueKeys(KeyList("id", farms.map(_.id))))
        } yield fs)
        .futureValue should equal(farms.map(Right(_)))
    }
  }

  it("should return old item after put asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"),100)))
        result <- farmersTable.putAndReturn(PutReturn.OldValue)(Farmer("McDonald", 50L, Farm(List("chicken", "cow"),100)))
      } yield result

      scanamo.exec(farmerOps).futureValue should equal(
        Some(Right(Farmer("McDonald", 156L, Farm(List("sheep", "cow"),100))))
      )
    }
  }

  it("should return None when putting a new item asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        result <- farmersTable.putAndReturn(PutReturn.OldValue)(Farmer("McDonald", 156L, Farm(List("sheep", "cow"),100)))
      } yield result

      scanamo.exec(farmerOps).futureValue should equal(
        None
      )
    }
  }

  it("conditionally put asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)

      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"),100)))
        _ <- farmersTable.when(AttributeName.of("age") === 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"),100)))
        _ <- farmersTable.when(AttributeName.of("age") === 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"),100)))
        farmerWithNewStock <- farmersTable.get(AttributeName.of("name") === "McDonald")
      } yield farmerWithNewStock

      scanamo.exec(farmerOps).futureValue should equal(
        Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "chicken"),100))))
      )
    }
  }

  it("conditionally put asynchronously with `between` condition") {
    LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)

      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 55, Farm(List("sheep", "cow"),100)))
        _ <- farmersTable.put(Farmer("Butch", 57, Farm(List("cattle"),100)))
        _ <- farmersTable.put(Farmer("Wade", 58, Farm(List("chicken", "sheep"),100)))
        _ <- farmersTable.when("age" between 56 and 57).put(Farmer("Butch", 57, Farm(List("chicken"),100)))
        _ <- farmersTable.when("age" between 58 and 59).put(Farmer("Butch", 57, Farm(List("dinosaur"),100)))
        farmerButch <- farmersTable.get(AttributeName.of("name") === "Butch")
      } yield farmerButch
      scanamo.exec(farmerOps).futureValue should equal(
        Some(Right(Farmer("Butch", 57, Farm(List("chicken"),100))))
      )
    }
  }

  it("conditionally delete asynchronously") {
    LocalDynamoDB.usingRandomTable(client)("number" -> N) { t =>
      val gremlinsTable = Table[Gremlin](t)

      val ops = for {
        _ <- gremlinsTable.putAll(Set(Gremlin(1, false,true), Gremlin(2, true,false)))
        _ <- gremlinsTable.when(AttributeName.of("wet") === true).delete(AttributeName.of("number") === 1)
        _ <- gremlinsTable.when(AttributeName.of("wet") === true).delete(AttributeName.of("number") === 2)
        remainingGremlins <- gremlinsTable.scan()
      } yield remainingGremlins

      scanamo.exec(ops).futureValue should equal(
        List(Right(Gremlin(1, false,true)))
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

      scanamo.exec(ops).futureValue should equal(
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
          _ <- gremlinTable.putAll(Set(Gremlin(1, wet = false,true), Gremlin(2, wet = true,false)))
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

        scanamo.exec(ops).futureValue should equal(
          (
            List(Right(Gremlin(2, wet = true,false)), Right(Gremlin(1, wet = false,true))),
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

      scanamo.exec(ops).futureValue should equal(
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
          _ <- gremlinTable.putAll(Set(Gremlin(1, wet = false,true), Gremlin(2, wet = true,false)))
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

        scanamo.exec(ops).futureValue should equal(
          (List(Right(Gremlin(1, wet = false,true))), List(Right(Forecast("Amsterdam", "Fog", None))))
        )
      }
    }
  }
}
