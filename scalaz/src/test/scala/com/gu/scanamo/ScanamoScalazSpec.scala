package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query._
import scalaz.ioeffect.RTS


class ScanamoScalazSpec extends FunSpec with Matchers with BeforeAndAfterAll with RTS {

  val client = LocalDynamoDB.client()

  override protected def afterAll(): Unit = {
    client.shutdown()
    super.afterAll()
  }

  it("should put asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      import com.gu.scanamo.syntax._

      val result = for {
        _ <- ScanamoScalaz.put[Farmer](client)(t)(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
      } yield Scanamo.get[Farmer](client)(t)('name -> "McDonald")

      unsafePerformIO(result) should equal(Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "cow"))))))
    }
  }

  it("should get asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      Scanamo.put[Farmer](client)(t)(Farmer("Maggot", 75L, Farm(List("dog"))))

      unsafePerformIO(ScanamoScalaz.get[Farmer](client)(t)(UniqueKey(KeyEquals('name, "Maggot")))) should
        equal(Some(Right(Farmer("Maggot", 75, Farm(List("dog"))))))

      import com.gu.scanamo.syntax._

      unsafePerformIO(ScanamoScalaz.get[Farmer](client)(t)('name -> "Maggot")) should
        equal(Some(Right(Farmer("Maggot", 75, Farm(List("dog"))))))
    }

    LocalDynamoDB.usingRandomTable(client)('name -> S, 'number -> N) { t =>
      case class Engine(name: String, number: Int)

      Scanamo.put(client)(t)(Engine("Thomas", 1))

      import com.gu.scanamo.syntax._
      unsafePerformIO(ScanamoScalaz.get[Engine](client)(t)('name -> "Thomas" and 'number -> 1)) should
        equal(Some(Right(Engine("Thomas", 1))))
    }
  }

  it("should get consistently asynchronously") {
    case class City(name: String, country: String)
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>

      import com.gu.scanamo.syntax._
      val _ = unsafePerformIO(ScanamoScalaz.put[City](client)(t)(City("Nashville", "US")))
      unsafePerformIO(ScanamoScalaz.getWithConsistency[City](client)(t)('name -> "Nashville")) should
        equal(Some(Right(City("Nashville", "US"))))
    }
  }

  it("should delete asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>

      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      Scanamo.put(client)(t)(Farmer("McGregor", 62L, Farm(List("rabbit"))))

      import com.gu.scanamo.syntax._

      val maybeFarmer = for {
        _ <- ScanamoScalaz.delete[Farmer](client)(t)('name -> "McGregor")
      } yield Scanamo.get[Farmer](client)(t)('name -> "McGregor")

      unsafePerformIO(maybeFarmer) should equal(None)
    }
  }

  it("should deleteAll asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>

      case class Farm(asyncAnimals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      import com.gu.scanamo.syntax._

      val dataSet = Set(
        Farmer("Patty", 200L, Farm(List("unicorn"))),
        Farmer("Ted", 40L, Farm(List("T-Rex"))),
        Farmer("Jack", 2L, Farm(List("velociraptor")))
      )

      Scanamo.putAll(client)(t)(dataSet)

      val maybeFarmer = for {
        _ <- ScanamoScalaz.deleteAll(client)(t)('name -> dataSet.map(_.name))
      } yield Scanamo.scan[Farmer](client)(t)

      unsafePerformIO(maybeFarmer) should equal(List.empty)
    }
  }

  it("should update asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('location -> S) { t =>

      case class Forecast(location: String, weather: String)

      Scanamo.put(client)(t)(Forecast("London", "Rain"))

      import com.gu.scanamo.syntax._

      val forecasts = for {
        _ <- ScanamoScalaz.update[Forecast](client)(t)('location -> "London", set('weather -> "Sun"))
      } yield Scanamo.scan[Forecast](client)(t)

      unsafePerformIO(forecasts) should equal(List(Right(Forecast("London", "Sun"))))
    }
  }

  it("should update asynchronously if a condition holds") {
    LocalDynamoDB.usingRandomTable(client)('location -> S) { t =>

      case class Forecast(location: String, weather: String, equipment: Option[String])

      val forecasts = Table[Forecast](t)

      import com.gu.scanamo.syntax._

      val ops = for {
        _ <- forecasts.putAll(Set(Forecast("London", "Rain", None), Forecast("Birmingham", "Sun", None)))
        _ <- forecasts.given('weather -> "Rain").update('location -> "London", set('equipment -> Some("umbrella")))
        _ <- forecasts.given('weather -> "Rain").update('location -> "Birmingham", set('equipment -> Some("umbrella")))
        results <- forecasts.scan()
      } yield results

      unsafePerformIO(ScanamoScalaz.exec[List[Either[DynamoReadError, Forecast]]](client)(ops)) should equal(
        List(Right(Forecast("London", "Rain", Some("umbrella"))), Right(Forecast("Birmingham", "Sun", None))))
    }
  }

  it("should scan asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>

      case class Bear(name: String, favouriteFood: String)

      Scanamo.put(client)(t)(Bear("Pooh", "honey"))
      Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets"))

      unsafePerformIO(ScanamoScalaz.scan[Bear](client)(t)) should equal(
        List(Right(Bear("Pooh", "honey")), Right(Bear("Yogi", "picnic baskets")))
      )
    }

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>

      case class Lemming(name: String, stuff: String)

      Scanamo.putAll(client)(t)(
        (for {_ <- 0 until 100} yield Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toSet
      )

      unsafePerformIO(ScanamoScalaz.scan[Lemming](client)(t)).size should equal(100)
    }
  }

  it("scans with a limit asynchronously") {
    case class Bear(name: String, favouriteFood: String)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      Scanamo.put(client)(t)(Bear("Pooh", "honey"))
      Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets"))
      val results = ScanamoScalaz.scanWithLimit[Bear](client)(t, 1)
      unsafePerformIO(results) should equal(List(Right(Bear("Pooh","honey"))))
    }
  }

  it("paginates with a limit asynchronously") {
    case class Bear(name: String, favouriteFood: String)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      Scanamo.put(client)(t)(Bear("Pooh", "honey"))
      Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets"))
      Scanamo.put(client)(t)(Bear("Graham", "quinoa"))
      val results = for {
        res1 <- ScanamoScalaz.scanFrom[Bear](client)(t, 1, None)
        res2 <- ScanamoScalaz.scanFrom[Bear](client)(t, 1, res1._2)
        res3 <- ScanamoScalaz.scanFrom[Bear](client)(t, 1, res2._2)
      } yield res2._1 ::: res3._1
      unsafePerformIO(results) should equal(List(Right(Bear("Yogi","picnic baskets")), Right(Bear("Graham","quinoa"))))
    }
  }

  it ("scanIndexWithLimit") {
    case class Bear(name: String, favouriteFood: String, alias: Option[String])

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('name -> S)('alias -> S) { (t, i) =>
      Scanamo.put(client)(t)(Bear("Pooh", "honey", Some("Winnie")))
      Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets", None))
      Scanamo.put(client)(t)(Bear("Graham", "quinoa", Some("Guardianista")))
      val results = ScanamoScalaz.scanIndexWithLimit[Bear](client)(t, i, 1)
      unsafePerformIO(results) should equal(List(Right(Bear("Graham","quinoa",Some("Guardianista")))))
    }
  }

  it ("Paginate scanIndexWithLimit") {
    case class Bear(name: String, favouriteFood: String, alias: Option[String])

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('name -> S)('alias -> S) { (t, i) =>
      Scanamo.put(client)(t)(Bear("Pooh", "honey", Some("Winnie")))
      Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets", Some("Kanga")))
      Scanamo.put(client)(t)(Bear("Graham", "quinoa", Some("Guardianista")))
      val results = for {
        res1 <- ScanamoScalaz.scanIndexFrom[Bear](client)(t, i, 1, None)
        res2 <- ScanamoScalaz.scanIndexFrom[Bear](client)(t, i, 1, res1._2)
        res3 <- ScanamoScalaz.scanIndexFrom[Bear](client)(t, i, 1, res2._2)
      } yield res2._1 ::: res3._1

      unsafePerformIO(results) should equal(List(Right(Bear("Yogi","picnic baskets",Some("Kanga"))), Right(Bear("Pooh","honey",Some("Winnie")))))
    }
  }

  it("should query asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('species -> S, 'number -> N) { t =>

      case class Animal(species: String, number: Int)

      Scanamo.put(client)(t)(Animal("Wolf", 1))

      for {i <- 1 to 3} Scanamo.put(client)(t)(Animal("Pig", i))

      import com.gu.scanamo.syntax._

      unsafePerformIO(ScanamoScalaz.query[Animal](client)(t)('species -> "Pig")) should equal(
        List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2)), Right(Animal("Pig", 3))))

      unsafePerformIO(ScanamoScalaz.query[Animal](client)(t)('species -> "Pig" and 'number < 3)) should equal(
        List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2))))
      unsafePerformIO(ScanamoScalaz.query[Animal](client)(t)('species -> "Pig" and 'number > 1)) should equal(
        List(Right(Animal("Pig", 2)), Right(Animal("Pig", 3))))
      unsafePerformIO(ScanamoScalaz.query[Animal](client)(t)('species -> "Pig" and 'number <= 2)) should equal(
        List(Right(Animal("Pig", 1)), Right(Animal("Pig", 2))))
      unsafePerformIO(ScanamoScalaz.query[Animal](client)(t)('species -> "Pig" and 'number >= 2)) should equal(
        List(Right(Animal("Pig", 2)), Right(Animal("Pig", 3))))

    }

    LocalDynamoDB.usingRandomTable(client)('mode -> S, 'line -> S) { t =>

      case class Transport(mode: String, line: String)

      import com.gu.scanamo.syntax._

      Scanamo.putAll(client)(t)(Set(
        Transport("Underground", "Circle"),
        Transport("Underground", "Metropolitan"),
        Transport("Underground", "Central")))

      unsafePerformIO(ScanamoScalaz.query[Transport](client)(t)('mode -> "Underground" and ('line beginsWith "C"))) should equal(
        List(Right(Transport("Underground", "Central")), Right(Transport("Underground", "Circle"))))
    }
  }

  it ("queries with a limit asynchronously") {
    import com.gu.scanamo.syntax._

    case class Transport(mode: String, line: String)

    LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
      Scanamo.putAll(client)(t)(Set(
        Transport("Underground", "Circle"),
        Transport("Underground", "Metropolitan"),
        Transport("Underground", "Central")))
      val results = ScanamoScalaz.queryWithLimit[Transport](client)(t)('mode -> "Underground" and ('line beginsWith "C"), 1)
      unsafePerformIO(results) should equal(List(Right(Transport("Underground","Central"))))
    }
  }

  it ("queries an index with a limit asynchronously") {
    case class Transport(mode: String, line: String, colour: String)

    import com.gu.scanamo.syntax._

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)(
      'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    ) { (t, i) =>
      Scanamo.putAll(client)(t)(Set(
        Transport("Underground", "Circle", "Yellow"),
        Transport("Underground", "Metropolitan", "Magenta"),
        Transport("Underground", "Central", "Red"),
        Transport("Underground", "Picadilly", "Blue"),
        Transport("Underground", "Northern", "Black")))
      val results = ScanamoScalaz.queryIndexWithLimit[Transport](client)(t, i)(
        'mode -> "Underground" and ('colour beginsWith "Bl"), 1)

      unsafePerformIO(results) should equal(List(Right(Transport("Underground","Northern","Black"))))
    }
  }

  it ("queries an index asynchronously with 'between' sort-key condition") {
    case class Station(mode: String, name: String, zone: Int)

    import com.gu.scanamo.syntax._

    def deletaAllStations(client: AmazonDynamoDBAsync, tableName: String, stations: Set[Station]) = {
      ScanamoScalaz.delete[Station](client)(tableName)('mode -> "Underground")
      ScanamoScalaz.deleteAll(client)(tableName)(
        UniqueKeys(MultipleKeyList(('mode, 'name), stations.map(station => (station.mode, station.name))))
      )
    }
    val LiverpoolStreet = Station("Underground", "Liverpool Street", 1)
    val CamdenTown = Station("Underground", "Camden Town", 2)
    val GoldersGreen = Station("Underground", "Golders Green", 3)
    val Hainault = Station("Underground", "Hainault", 4)

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)(
      'mode -> S, 'name -> S)('mode -> S, 'zone -> N
    ) { (t, i) =>
      val stations = Set(LiverpoolStreet, CamdenTown, GoldersGreen, Hainault)
      Scanamo.putAll(client)(t)(stations)
      val results1 = ScanamoScalaz.queryIndex[Station](client)(t, i)(
        'mode -> "Underground" and ('zone between (2 and 4)))

      unsafePerformIO(results1) should equal(List(Right(CamdenTown), Right(GoldersGreen), Right(Hainault)))

      val maybeStations1 = for {_ <- deletaAllStations(client, t, stations)} yield Scanamo.scan[Station](client)(t)
      unsafePerformIO(maybeStations1) should equal(List.empty)

      Scanamo.putAll(client)(t)(Set(LiverpoolStreet))
      val results2 = ScanamoScalaz.queryIndex[Station](client)(t, i)(
        'mode -> "Underground" and ('zone between (2 and 4)))
      unsafePerformIO(results2) should equal(List.empty)

      val maybeStations2 = for {_ <- deletaAllStations(client, t, stations)} yield Scanamo.scan[Station](client)(t)
      unsafePerformIO(maybeStations2) should equal(List.empty)

      Scanamo.putAll(client)(t)(Set(CamdenTown))
      val results3 = ScanamoScalaz.queryIndex[Station](client)(t, i)(
        'mode -> "Underground" and ('zone between (1 and 1)))
      unsafePerformIO(results3) should equal(List.empty)
    }
  }

  it("queries for items that are missing an attribute") {
    case class Farmer(firstName: String, surname: String, age: Option[Int])

    import com.gu.scanamo.syntax._

    LocalDynamoDB.usingRandomTable(client)('firstName -> S, 'surname -> S) { t =>
      val farmersTable = Table[Farmer](t)

      val farmerOps: ScanamoOps[List[Either[DynamoReadError, Farmer]]] = for {
        _ <- farmersTable.put(Farmer("Fred", "Perry", None))
        _ <- farmersTable.put(Farmer("Fred", "McDonald", Some(54)))
        farmerWithNoAge <- farmersTable.filter(attributeNotExists('age)).query('firstName -> "Fred")
      } yield farmerWithNoAge
      unsafePerformIO(ScanamoScalaz.exec[List[Either[DynamoReadError, Farmer]]](client)(farmerOps)) should equal(List(Right(Farmer("Fred", "Perry", None))))
    }
  }

  it("should put multiple items asynchronously") {
    case class Rabbit(name: String)

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val result = for {
        _ <- ScanamoScalaz.putAll[Rabbit](client)(t)((
          for {_ <- 0 until 100} yield Rabbit(util.Random.nextString(500))
          ).toSet)
      } yield Scanamo.scan[Rabbit](client)(t)

      unsafePerformIO(result).size should equal(100)
    }
    ()
  }

  it("should get multiple items asynchronously") {
    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>

      case class Farm(animals: List[String])
      case class Farmer(name: String, age: Long, farm: Farm)

      Scanamo.putAll(client)(t)(Set(
        Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
      ))

      unsafePerformIO(ScanamoScalaz.getAll[Farmer](client)(t)(
        UniqueKeys(KeyList('name, Set("Boggis", "Bean")))
      )) should equal(
        Set(Right(Farmer("Boggis", 43, Farm(List("chicken")))), Right(Farmer("Bean", 55, Farm(List("turkey"))))))

      import com.gu.scanamo.syntax._

      unsafePerformIO(ScanamoScalaz.getAll[Farmer](client)(t)('name -> Set("Boggis", "Bean"))) should equal(
        Set(Right(Farmer("Boggis", 43, Farm(List("chicken")))), Right(Farmer("Bean", 55, Farm(List("turkey"))))))
    }

    LocalDynamoDB.usingRandomTable(client)('actor -> S, 'regeneration -> N) { t =>
      case class Doctor(actor: String, regeneration: Int)

      Scanamo.putAll(client)(t)(
        Set(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))

      import com.gu.scanamo.syntax._
      unsafePerformIO(ScanamoScalaz.getAll[Doctor](client)(t)(
        ('actor and 'regeneration) -> Set("McCoy" -> 9, "Ecclestone" -> 11)
      )) should equal(
        Set(Right(Doctor("McCoy", 9)), Right(Doctor("Ecclestone", 11))))

    }
  }

  it("should get multiple items asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingRandomTable(client)('id -> N) { t =>

      case class Farm(id: Int, name: String)
      val farms = (1 to 101).map(i => Farm(i, s"Farm #$i")).toSet

      Scanamo.putAll(client)(t)(farms)

      unsafePerformIO(ScanamoScalaz.getAll[Farm](client)(t)(
        UniqueKeys(KeyList('id, farms.map(_.id)))
      )) should equal(farms.map(Right(_)))
    }
  }

  it("should get multiple items consistently asynchronously (automatically handling batching)") {
    LocalDynamoDB.usingRandomTable(client)('id -> N) { t =>

      case class Farm(id: Int, name: String)
      val farms = (1 to 101).map(i => Farm(i, s"Farm #$i")).toSet

      Scanamo.putAll(client)(t)(farms)

      unsafePerformIO(ScanamoScalaz.getAllWithConsistency[Farm](client)(t)(
        UniqueKeys(KeyList('id, farms.map(_.id)))
      )) should equal(farms.map(Right(_)))
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
      unsafePerformIO(ScanamoScalaz.exec[Option[Either[DynamoReadError, Farmer]]](client)(farmerOps)) should equal(
        Some(Right(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))))
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
      unsafePerformIO(ScanamoScalaz.exec[Option[Either[DynamoReadError, Farmer]]](client)(farmerOps)) should equal(None)
    }
  }

  it("conditionally put asynchronously") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    import com.gu.scanamo.syntax._

    LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
      val farmersTable = Table[Farmer](t)

      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
        _ <- farmersTable.given('age -> 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"))))
        _ <- farmersTable.given('age -> 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"))))
        farmerWithNewStock <- farmersTable.get('name -> "McDonald")
      } yield farmerWithNewStock
      unsafePerformIO(ScanamoScalaz.exec[Option[Either[DynamoReadError, Farmer]]](client)(farmerOps)) should equal(
        Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "chicken"))))))
    }
  }

  it("conditionally put asynchronously with 'between' condition") {
    case class Farm(animals: List[String])
    case class Farmer(name: String, age: Long, farm: Farm)

    import com.gu.scanamo.syntax._

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
      unsafePerformIO(ScanamoScalaz.exec[Option[Either[DynamoReadError, Farmer]]](client)(farmerOps)) should equal(
        Some(Right(Farmer("Butch", 57, Farm(List("chicken")))))
      )
    }
  }

  it("conditionally delete asynchronously") {
    case class Gremlin(number: Int, wet: Boolean)

    import com.gu.scanamo.syntax._

    LocalDynamoDB.usingRandomTable(client)('number -> N) { t =>
      val gremlinsTable = Table[Gremlin](t)

      val ops = for {
        _ <- gremlinsTable.putAll(Set(Gremlin(1, false), Gremlin(2, true)))
        _ <- gremlinsTable.given('wet -> true).delete('number -> 1)
        _ <- gremlinsTable.given('wet -> true).delete('number -> 2)
        remainingGremlins <- gremlinsTable.scan()
      } yield remainingGremlins
      unsafePerformIO(ScanamoScalaz.exec[List[Either[DynamoReadError, Gremlin]]](client)(ops)) should equal(List(Right(Gremlin(1,false))))
    }
  }
}
