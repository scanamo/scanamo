package org.scanamo

import cats.implicits._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
import org.scanamo.query._
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import org.scanamo.ops.ScanamoOps

object TableTest {
  case class Bar(name: String, counter: Long, set: Set[String])
  case class Bear(name: String, favouriteFood: String)
  case class Character(name: String, actors: List[String])
  case class Choice(number: Int, description: String)
  case class City(country: String, name: String)
  case class Compound(a: String, maybe: Option[Int])
  case class Event(`type`: String, tag: String, count: Int)
  case class Farm(animals: List[String], hectares: Int)
  case class Farmer(name: String, age: Long, farm: Farm)
  case class Foo(name: String, bar: Int, l: List[String])
  case class Forecast(location: String, weather: String)
  case class Fruit(kind: String, sources: List[String])
  case class GithubProject(organisation: String, repository: String, language: String, license: String)
  case class Gremlin(number: Int, wet: Boolean, friendly: Boolean)
  case class Inner(session: String)
  case class Letter(roman: String, greek: String)
  case class Middle(name: String, counter: Long, inner: Inner, list: List[Int])
  case class Outer(id: java.util.UUID, middle: Middle)
  case class Station(line: String, name: String, zone: Int)
  case class Thing(id: String, mandatory: Int, optional: Option[Int])
  case class Thing2(a: String, maybe: Option[Int])
  case class Transport(mode: String, line: String, colour: String)
  case class Turnip(size: Int, description: Option[String])
}

class TableTest extends AnyFunSpec with Matchers {
  import TableTest._

  val client = LocalDynamoDB.syncClient()
  val scanamo = Scanamo(client)

  it("Put multiple items and them queries") {
    LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
      val transport = Table[Transport](t)
      val operations = for {
        _ <- transport.putAll(
          Set(
            Transport("Underground", "Circle", "Yellow"),
            Transport("Underground", "Metropolitan", "Magenta"),
            Transport("Underground", "Central", "Red")
          )
        )
        results <- transport.query("mode" -> "Underground" and ("line" beginsWith "C"))
      } yield results.toList
      scanamo.exec(operations) should be(
        List(Right(Transport("Underground", "Central", "Red")), Right(Transport("Underground", "Circle", "Yellow")))
      )
    }
  }

  it("Deletes multiple items by a unique key") {
    val dataSet = Set(
      Farmer("Patty", 200L, Farm(List("unicorn"), 10)),
      Farmer("Ted", 40L, Farm(List("T-Rex"), 20)),
      Farmer("Jack", 2L, Farm(List("velociraptor"), 30))
    )
    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val farm = Table[Farmer](t)
      val operations = for {
        _ <- farm.putAll(dataSet)
        _ <- farm.deleteAll("name" -> dataSet.map(_.name))
        scanned <- farm.scan
      } yield scanned.toList
      scanamo.exec(operations) should be(Nil)
    }
  }

  it("Queries via a secondary index") {
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("mode" -> S, "line" -> S)("colour" -> S) { (t, i) =>
      val transport = Table[Transport](t)
      val operations = for {
        _ <- transport.putAll(
          Set(
            Transport("Underground", "Circle", "Yellow"),
            Transport("Underground", "Metropolitan", "Magenta"),
            Transport("Underground", "Central", "Red")
          )
        )
        MagentaLine <- transport.index(i).query("colour" -> "Magenta")
      } yield MagentaLine.toList
      scanamo.exec(operations) should be(List(Right(Transport("Underground", "Metropolitan", "Magenta"))))
    }

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("organisation" -> S, "repository" -> S)(
      "language" -> S,
      "license" -> S
    ) { (t, i) =>
      val githubProjects = Table[GithubProject](t)
      val operations = for {
        _ <- githubProjects.putAll(
          Set(
            GithubProject("typelevel", "cats", "Scala", "MIT"),
            GithubProject("localytics", "sbt-dynamodb", "Scala", "MIT"),
            GithubProject("tpolecat", "tut", "Scala", "MIT"),
            GithubProject("guardian", "scanamo", "Scala", "Apache 2")
          )
        )
        scalaMIT <- githubProjects.index(i).query("language" -> "Scala" and ("license" -> "MIT"))
      } yield scalaMIT.toList
      scanamo.exec(operations) should be(
        List(
          Right(GithubProject("typelevel", "cats", "Scala", "MIT")),
          Right(GithubProject("tpolecat", "tut", "Scala", "MIT")),
          Right(GithubProject("localytics", "sbt-dynamodb", "Scala", "MIT"))
        )
      )
    }
  }

  it("Sets an attribute") {

    LocalDynamoDB.withRandomTable(client)("location" -> S) { t =>
      val forecast = Table[Forecast](t)
      val operations = for {
        _ <- forecast.put(Forecast("London", "Rain"))
        updated <- forecast.update("location" -> "London", set("weather" -> "Sun"))
      } yield updated
      scanamo.exec(operations) should be(Right(Forecast("London", "Sun")))
    }

  }

  it("List attributes can also be appended or prepended to") {

    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val characters = Table[Character](t)
      val operations = for {
        _ <- characters.put(Character("The Doctor", List("Ecclestone", "Tennant", "Smith")))
        _ <- characters.update("name" -> "The Doctor", append("actors" -> "Capaldi"))
        _ <- characters.update("name" -> "The Doctor", prepend("actors" -> "McCoy"))
        results <- characters.scan()
      } yield results.toList
      scanamo.exec(operations) should be(
        List(Right(Character("The Doctor", List("McCoy", "Ecclestone", "Tennant", "Smith", "Capaldi"))))
      )
    }

  }

  it("Appending or prepending creates the list if it does not yet exist:") {

    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val characters = Table[Character](t)
      val operations = for {
        _ <- characters.update("name" -> "James Bond", append("actors" -> "Craig"))
        results <- characters.query("name" -> "James Bond")
      } yield results.toList
      scanamo.exec(operations) should be(List(Right(Character("James Bond", List("Craig")))))
    }
  }

  it("To concatenate a list to the front or end of an existing list, use appendAll/prependAll:") {

    LocalDynamoDB.withRandomTable(client)("kind" -> S) { t =>
      val fruits = Table[Fruit](t)
      val operations = for {
        _ <- fruits.put(Fruit("watermelon", List("USA")))
        _ <- fruits.update("kind" -> "watermelon", appendAll("sources" -> List("China", "Turkey")))
        _ <- fruits.update("kind" -> "watermelon", prependAll("sources" -> List("Brazil")))
        results <- fruits.query("kind" -> "watermelon")
      } yield results.toList
      scanamo.exec(operations) should be(List(Right(Fruit("watermelon", List("Brazil", "USA", "China", "Turkey")))))
    }
  }

  it("Multiple operations can also be performed in one call:") {

    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val foos = Table[Foo](t)
      val operations = for {
        _ <- foos.put(Foo("x", 0, List("First")))
        updated <- foos.update("name" -> "x", append("l" -> "Second") and set("bar" -> 1))
      } yield updated
      scanamo.exec(operations) should be(Right(Foo("x", 1, List("First", "Second"))))
    }

  }

  it("It's also possible to perform `ADD` and `DELETE` updates") {

    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val bars = Table[Bar](t)
      val operations = for {
        _ <- bars.put(Bar("x", 1L, Set("First")))
        _ <- bars.update("name" -> "x", add("counter" -> 10L) and add("set" -> Set("Second")))
        updatedBar <- bars.update("name" -> "x", delete("set" -> Set("First")))
      } yield updatedBar
      scanamo.exec(operations) should be(Right(Bar("x", 11, Set("Second"))))
    }
  }

  it("Updates may occur on nested attributes") {

    LocalDynamoDB.withRandomTable(client)("id" -> S) { t =>
      val outers = Table[Outer](t)
      val id = java.util.UUID.fromString("a8345373-9a93-43be-9bcd-e3682c9197f4")
      val operations = for {
        _ <- outers.put(Outer(id, Middle("x", 1L, Inner("alpha"), List(1, 2))))
        updatedOuter <-
          outers.update("id" -> id, set("middle" \ "inner" \ "session" -> "beta") and add(("middle" \ "list")(1) -> 1))
      } yield updatedOuter
      scanamo.exec(operations) should be(Right(Outer(id, Middle("x", 1, Inner("beta"), List(1, 3)))))
    }
  }

  it("It's possible to update one field to the value of another") {

    LocalDynamoDB.withRandomTable(client)("id" -> S) { t =>
      val things = Table[Thing](t)
      val operations = for {
        _ <- things.put(Thing("a1", 3, None))
        updated <- things.update("id" -> "a1", set("optional", "mandatory"))
      } yield updated
      scanamo.exec(operations) should be(Right(Thing("a1", 3, Some(3))))
    }
  }

  it("Query or scan a table, limiting the number of items evaluated by Dynamo") {

    LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
      val transport = Table[Transport](t)
      val operations = for {
        _ <- transport.putAll(
          Set(
            Transport("Underground", "Circle", "Yellow"),
            Transport("Underground", "Metropolitan", "Magenta"),
            Transport("Underground", "Central", "Red")
          )
        )
        results <- transport.limit(1).query("mode" -> "Underground" and ("line" beginsWith "C"))
      } yield results.toList
      scanamo.exec(operations) should be(List(Right(Transport("Underground", "Central", "Red"))))
    }
  }

  it("Perform strongly consistent read operations against this table") {

    val (get, scan, query) = LocalDynamoDB.withRandomTable(client)("country" -> S, "name" -> S) { t =>
      import org.scanamo.syntax._
      import org.scanamo.generic.auto._
      val cityTable = Table[City](t)
      val ops = for {
        _ <- cityTable.putAll(
          Set(City("US", "Nashville"), City("IT", "Rome"), City("IT", "Siena"), City("TZ", "Dar es Salaam"))
        )
        get <- cityTable.consistently.get("country" -> "US" and "name" -> "Nashville")
        scan <- cityTable.consistently.scan()
        query <- cityTable.consistently.query("country" -> "IT")
      } yield (get, scan, query)
      scanamo.exec(ops)
    }
    get should be(Some(Right(City("US", "Nashville"))))
    scan should be(
      List(
        Right(City("US", "Nashville")),
        Right(City("IT", "Rome")),
        Right(City("IT", "Siena")),
        Right(City("TZ", "Dar es Salaam"))
      )
    )

    query should be(List(Right(City("IT", "Rome")), Right(City("IT", "Siena"))))
  }

  it("Performs the chained operation, `put` if the condition is met") {

    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val farmersTable = Table[Farmer](t)
      val farmerOps = for {
        _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"), 30)))
        _ <- farmersTable.given("age" -> 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"), 30)))
        _ <- farmersTable.given("age" -> 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"), 30)))
        farmerWithNewStock <- farmersTable.get("name" -> "McDonald")
      } yield farmerWithNewStock
      scanamo.exec(farmerOps) should be(Some(Right(Farmer("McDonald", 156, Farm(List("sheep", "chicken"), 30)))))
    }

    LocalDynamoDB.withRandomTable(client)("roman" -> S) { t =>
      val lettersTable = Table[Letter](t)
      val ops = for {
        _ <- lettersTable.putAll(Set(Letter("a", "alpha"), Letter("b", "beta"), Letter("c", "gammon")))
        _ <- lettersTable.given("greek" beginsWith "ale").put(Letter("a", "aleph"))
        _ <- lettersTable.given("greek" beginsWith "gam").put(Letter("c", "gamma"))
        letters <- lettersTable.scan()
      } yield letters
      scanamo.exec(ops).toList should be(
        List(Right(Letter("b", "beta")), Right(Letter("c", "gamma")), Right(Letter("a", "alpha")))
      )
    }

    LocalDynamoDB.withRandomTable(client)("size" -> N) { t =>
      val turnipsTable = Table[Turnip](t)
      val ops = for {
        _ <- turnipsTable.putAll(Set(Turnip(1, None), Turnip(1000, None)))
        initialTurnips <- turnipsTable.scan()
        _ <-
          initialTurnips
            .flatMap(_.toOption)
            .traverse(t =>
              turnipsTable.given("size" > 500).put(t.copy(description = Some("Big turnip in the country.")))
            )
        turnips <- turnipsTable.scan()
      } yield turnips
      scanamo.exec(ops).toList should be(
        List(Right(Turnip(1, None)), Right(Turnip(1000, Some("Big turnip in the country."))))
      )
    }
  }

  it("Conditions can also make use of combinators") {

    LocalDynamoDB.withRandomTable(client)("a" -> S) { t =>
      val thingTable = Table[Thing2](t)
      val ops = for {
        _ <- thingTable.putAll(Set(Thing2("a", None), Thing2("b", Some(1)), Thing2("c", None)))
        _ <- thingTable.given(attributeExists("maybe")).put(Thing2("a", Some(2)))
        _ <- thingTable.given(attributeExists("maybe")).put(Thing2("b", Some(3)))
        _ <- thingTable.given(Not(attributeExists("maybe"))).put(Thing2("c", Some(42)))
        _ <- thingTable.given(Not(attributeExists("maybe"))).put(Thing2("b", Some(42)))
        things <- thingTable.scan()
      } yield things
      scanamo.exec(ops).toList should be(
        List(Right(Thing2("b", Some(3))), Right(Thing2("c", Some(42))), Right(Thing2("a", None)))
      )
    }

    LocalDynamoDB.withRandomTable(client)("a" -> S) { t =>
      val compoundTable = Table[Compound](t)
      val ops = for {
        _ <- compoundTable.putAll(Set(Compound("alpha", None), Compound("beta", Some(1)), Compound("gamma", None)))
        _ <- compoundTable.given(Condition(attributeExists("maybe")) and "a" -> "alpha").put(Compound("alpha", Some(2)))
        _ <- compoundTable.given(Condition(attributeExists("maybe")) and "a" -> "beta").put(Compound("beta", Some(3)))
        _ <-
          compoundTable.given(Condition("a" -> "gamma") and attributeExists("maybe")).put(Compound("gamma", Some(42)))
        compounds <- compoundTable.scan()
      } yield compounds
      scanamo.exec(ops).toList should be(
        List(Right(Compound("beta", Some(3))), Right(Compound("alpha", None)), Right(Compound("gamma", None)))
      )
    }

    LocalDynamoDB.withRandomTable(client)("number" -> N) { t =>
      val choicesTable = Table[Choice](t)
      val ops = for {
        _ <- choicesTable.putAll(Set(Choice(1, "cake"), Choice(2, "crumble"), Choice(3, "custard")))
        _ <-
          choicesTable
            .given(Condition("description" -> "cake") or Condition("description" -> "death"))
            .put(Choice(1, "victoria sponge"))
        _ <-
          choicesTable
            .given(Condition("description" -> "cake") or Condition("description" -> "death"))
            .put(Choice(2, "victoria sponge"))
        choices <- choicesTable.scan()
      } yield choices
      scanamo.exec(ops).toList should be(
        List(Right(Choice(2, "crumble")), Right(Choice(1, "victoria sponge")), Right(Choice(3, "custard")))
      )
    }
  }

  it("The same forms of condition can be applied to deletions") {

    LocalDynamoDB.withRandomTable(client)("number" -> N) { t =>
      val gremlinsTable = Table[Gremlin](t)
      val ops = for {
        _ <- gremlinsTable.putAll(Set(Gremlin(1, false, true), Gremlin(2, true, false)))
        _ <- gremlinsTable.given("wet" -> true).delete("number" -> 1)
        _ <- gremlinsTable.given("wet" -> true).delete("number" -> 2)
        remainingGremlins <- gremlinsTable.scan()
      } yield remainingGremlins
      scanamo.exec(ops).toList should be(List(Right(Gremlin(1, false, true))))
    }

    LocalDynamoDB.withRandomTable(client)("number" -> N) { t =>
      val gremlinsTable = Table[Gremlin](t)
      val ops = for {
        _ <- gremlinsTable.putAll(Set(Gremlin(1, false, true), Gremlin(2, true, true)))
        _ <- gremlinsTable.given("wet" -> true).update("number" -> 1, set("friendly" -> false))
        _ <- gremlinsTable.given("wet" -> true).update("number" -> 2, set("friendly" -> false))
        remainingGremlins <- gremlinsTable.scan()
      } yield remainingGremlins
      scanamo.exec(ops).toList should be(List(Right(Gremlin(2, true, false)), Right(Gremlin(1, false, true))))
    }
  }

  it("Conditions can also be placed on nested attributes") {

    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val smallscaleFarmersTable = Table[Farmer](t)
      val farmerOps = for {
        _ <- smallscaleFarmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"), 30)))
        _ <-
          smallscaleFarmersTable
            .given("farm" \ "hectares" < 40L)
            .put(Farmer("McDonald", 156L, Farm(List("gerbil", "hamster"), 20)))
        _ <-
          smallscaleFarmersTable
            .given("farm" \ "hectares" > 40L)
            .put(Farmer("McDonald", 156L, Farm(List("elephant"), 50)))
        _ <-
          smallscaleFarmersTable
            .given("farm" \ "hectares" -> 20L)
            .update("name" -> "McDonald", append("farm" \ "animals" -> "squirrel"))
        farmerWithNewStock <- smallscaleFarmersTable.get("name" -> "McDonald")
      } yield farmerWithNewStock
      scanamo.exec(farmerOps) should be(
        Some(Right(Farmer("McDonald", 156, Farm(List("gerbil", "hamster", "squirrel"), 20))))
      )
    }
  }

  it("Primes a search request with a key to start from:") {

    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val table = Table[Bear](t)
      val ops = for {
        _ <- table.put(Bear("Pooh", "honey"))
        _ <- table.put(Bear("Baloo", "ants"))
        _ <- table.put(Bear("Yogi", "picnic baskets"))
        bears <- table.from("name" -> "Baloo").scan()
      } yield bears
      scanamo.exec(ops) should be(List(Right(Bear("Pooh", "honey")), Right(Bear("Yogi", "picnic baskets"))))
    }

    LocalDynamoDB.withRandomTable(client)("type" -> S, "tag" -> S) { t =>
      val table = Table[Event](t)
      val ops = for {
        _ <- table.putAll(
          Set(
            Event("click", "paid", 600),
            Event("play", "profile", 100),
            Event("play", "politics", 200),
            Event("click", "profile", 400),
            Event("play", "print", 600),
            Event("click", "print", 300),
            Event("play", "paid", 900)
          )
        )
        events <-
          table.from("type" -> "play" and "tag" -> "politics").query("type" -> "play" and ("tag" beginsWith "p"))
      } yield events
      scanamo.exec(ops) should be(List(Right(Event("play", "print", 600)), Right(Event("play", "profile", 100))))
    }
  }

  it("Scans all elements of a table") {

    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      import org.scanamo._
      import org.scanamo.generic.auto._
      val table = Table[Bear](t)
      val ops = for {
        _ <- table.put(Bear("Pooh", "honey"))
        _ <- table.put(Bear("Yogi", "picnic baskets"))
        bears <- table.scan()
      } yield bears
      scanamo.exec(ops) should be(List(Right(Bear("Pooh", "honey")), Right(Bear("Yogi", "picnic baskets"))))
    }
  }

  it("Scans the table and returns the raw DynamoDB result") {

    LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
      val table = Table[Transport](t)
      val ops = for {
        _ <- table.putAll(
          Set(
            Transport("Underground", "Circle", "Y"),
            Transport("Underground", "Metropolitan", "M"),
            Transport("Underground", "Central", "R")
          )
        )
        res <- table.limit(1).scan0
        uniqueKeyCondition =
          UniqueKeyCondition[AndEqualsCondition[KeyEquals[String], KeyEquals[String]], (AttributeName, AttributeName)]
        lastKey = uniqueKeyCondition.fromDynamoObject(("mode", "line"), DynamoObject(res.lastEvaluatedKey))
        ts <- lastKey.fold(List.empty[Either[DynamoReadError, Transport]].pure[ScanamoOps])(table.from(_).scan())
      } yield ts
      scanamo.exec(ops) should be(
        List(Right(Transport("Underground", "Circle", "Y")), Right(Transport("Underground", "Metropolitan", "M")))
      )
    }
  }

  it("Query a table based on the hash key and optionally the range key") {

    LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
      val table = Table[Transport](t)
      val ops = for {
        _ <- table.putAll(
          Set(
            Transport("Underground", "Circle", "Y"),
            Transport("Underground", "Metropolitan", "M"),
            Transport("Underground", "Central", "R")
          )
        )
        linesBeginningWithC <- table.query("mode" -> "Underground" and ("line" beginsWith "C"))
      } yield linesBeginningWithC
      scanamo.exec(ops) should be(
        List(Right(Transport("Underground", "Central", "R")), Right(Transport("Underground", "Circle", "Y")))
      )
    }
  }

  it("Queries the table and returns the raw DynamoDB result") {

    LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
      val table = Table[Transport](t)
      val ops = for {
        _ <- table.putAll(
          Set(
            Transport("Underground", "Circle", "Y"),
            Transport("Underground", "Metropolitan", "M"),
            Transport("Underground", "Central", "R"),
            Transport("Bus", "390", "R"),
            Transport("Bus", "143", "R"),
            Transport("Bus", "234", "R")
          )
        )
        res <- table.limit(1).query0("mode" -> "Bus" and "line" -> "234")
        uniqueKeyCondition =
          UniqueKeyCondition[AndEqualsCondition[KeyEquals[String], KeyEquals[String]], (AttributeName, AttributeName)]
        lastKey = uniqueKeyCondition.fromDynamoObject(("mode", "line"), DynamoObject(res.lastEvaluatedKey))
        ts <- lastKey.fold(List.empty[Either[DynamoReadError, Transport]].pure[ScanamoOps])(table.from(_).scan())
      } yield ts
      scanamo.exec(ops) should be(
        List(
          Right(Transport("Bus", "390", "R")),
          Right(Transport("Underground", "Central", "R")),
          Right(Transport("Underground", "Circle", "Y")),
          Right(Transport("Underground", "Metropolitan", "M"))
        )
      )
    }

  }

  it("Filter the results of a Scan or Query") {

    case class Bear(name: String, favouriteFood: String, antagonist: Option[String])
    LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
      val table = Table[Bear](t)
      val ops = for {
        _ <- table.put(Bear("Pooh", "honey", None))
        _ <- table.put(Bear("Yogi", "picnic baskets", Some("Ranger Smith")))
        honeyBears <- table.filter("favouriteFood" -> "honey").scan()
        competitiveBears <- table.filter(attributeExists("antagonist")).scan()
      } yield (honeyBears, competitiveBears)
      scanamo.exec(ops) should be(
        (
          List(Right(Bear("Pooh", "honey", None))),
          List(Right(Bear("Yogi", "picnic baskets", Some("Ranger Smith"))))
        )
      )
    }

    LocalDynamoDB.withRandomTable(client)("line" -> S, "name" -> S) { t =>
      val stationTable = Table[Station](t)
      val ops = for {
        _ <- stationTable.putAll(
          Set(
            Station("Metropolitan", "Chalfont & Latimer", 8),
            Station("Metropolitan", "Chorleywood", 7),
            Station("Metropolitan", "Rickmansworth", 7),
            Station("Metropolitan", "Croxley", 7),
            Station("Jubilee", "Canons Park", 5)
          )
        )
        filteredStations <-
          stationTable.filter("zone" -> Set(8, 7)).query("line" -> "Metropolitan" and ("name" beginsWith "C"))
      } yield filteredStations
      scanamo.exec(ops) should be(
        List(
          Right(Station("Metropolitan", "Chalfont & Latimer", 8)),
          Right(Station("Metropolitan", "Chorleywood", 7)),
          Right(Station("Metropolitan", "Croxley", 7))
        )
      )
    }
  }

  it("Filters a table with 11 condition expression attribute values, fixing #597") {
    LocalDynamoDB.withRandomTable(client)("line" -> S, "name" -> S) { t =>
      val stationTable = Table[Station](t)
      val ops = for {
        _ <- stationTable.putAll(
          Set(
            Station("Metropolitan", "Chalfont & Latimer", 8),
            Station("Metropolitan", "Chorleywood", 7),
            Station("Metropolitan", "Rickmansworth", 7),
            Station("Metropolitan", "Croxley", 7),
            Station("Jubilee", "Canons Park", 5)
          )
        )
        filteredStations <-
          stationTable
            .filter(
              (
                attributeExists("line") and
                  attributeExists("name") and
                  attributeNotExists("colour") and
                  Not(attributeExists("speed")) and
                  "zone" -> Set(8, 7, 5) and
                  ("line" beginsWith "Metr") and
                  ("name" beginsWith "C") and
                  ("zone" between 5 and 8) and
                  "name" -> "Chorleywood"
              ) or (
                "line" -> "Jubilee"
              )
            )
            .scan
      } yield filteredStations

      scanamo.exec(ops) should be(
        List(
          Right(Station("Metropolitan", "Chorleywood", 7)),
          Right(Station("Jubilee", "Canons Park", 5))
        )
      )
    }
  }
}
