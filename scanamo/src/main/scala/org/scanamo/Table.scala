package org.scanamo

import com.amazonaws.services.dynamodbv2.model.{ BatchWriteItemResult, DeleteItemResult, QueryResult, ScanResult }
import org.scanamo.DynamoResultStream.{ QueryResultStream, ScanResultStream }
import org.scanamo.error.DynamoReadError
import org.scanamo.ops.ScanamoOps
import org.scanamo.query._
import org.scanamo.request.{ ScanamoQueryOptions, ScanamoQueryRequest, ScanamoScanRequest }
import org.scanamo.update.UpdateExpression

/**
  * Represents a DynamoDB table that operations can be performed against
  *
  * {{{
  * >>> case class Transport(mode: String, line: String)
  *
  * >>> val client = LocalDynamoDB.client()
  * >>> val scanamo = Scanamo(client)
  * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
  *
  * >>> LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
  * ...   import org.scanamo.syntax._
  * ...   import org.scanamo.auto._
  * ...   val transport = Table[Composite, (String, String), Transport](t)
  * ...   val operations = for {
  * ...     _ <- transport.putAll(Set(
  * ...       Transport("Underground", "Circle"),
  * ...       Transport("Underground", "Metropolitan"),
  * ...       Transport("Underground", "Central")))
  * ...     results <- transport.query("mode" -> "Underground" and ("line" beginsWith "C"))
  * ...   } yield results.toList
  * ...   scanamo.exec(operations)
  * ... }
  * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
  * }}}
  */
case class Table[KT <: KeyType, K, V: DynamoFormat](name: String) {

  def put(v: V): ScanamoOps[Option[Either[DynamoReadError, V]]] = ScanamoFree.put(name)(v)
  def putAll(vs: Set[V]): ScanamoOps[List[BatchWriteItemResult]] = ScanamoFree.putAll(name)(vs)
  def get(key: Key[KT, K]): ScanamoOps[Option[Either[DynamoReadError, V]]] = ScanamoFree.get(name)(key, false)
  def getAll(keys: Iterable[Key[KT, K]]): ScanamoOps[Set[Either[DynamoReadError, V]]] =
    ScanamoFree.getAll(name)(keys, false)
  def delete(key: Key[KT, K]): ScanamoOps[DeleteItemResult] = ScanamoFree.delete(name)(key)

  /**
    * Deletes multiple items by a unique key
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    *
    * >>> val dataSet = Set(
    * ...   Farmer("Patty", 200L, Farm(List("unicorn"))),
    * ...   Farmer("Ted", 40L, Farm(List("T-Rex"))),
    * ...   Farmer("Jack", 2L, Farm(List("velociraptor"))))
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   val farm = Table[Simple, String, Farmer](t)
    * ...   val operations = for {
    * ...     _       <- farm.putAll(dataSet)
    * ...     _       <- farm.deleteAll("name" -> dataSet.map(_.name))
    * ...     scanned <- farm.scan
    * ...   } yield scanned.toList
    * ...   scanamo.exec(operations)
    * ... }
    * List()
    * }}}
    */
  def deleteAll(items: Iterable[Key[KT, K]]): ScanamoOps[List[BatchWriteItemResult]] =
    ScanamoFree.deleteAll(name)(items)

  /**
    * A secondary index on the table which can be scanned, or queried against
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("mode" -> S, "line" -> S)("colour" -> S) { (t, i) =>
    * ...   val transport = Table[Composite, (String, String), Transport](t)
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle", "Yellow"),
    * ...       Transport("Underground", "Metropolitan", "Magenta"),
    * ...       Transport("Underground", "Central", "Red")))
    * ...     MagentaLine <- transport.index(i).query("colour" -> "Magenta")
    * ...   } yield MagentaLine.toList
    * ...   scanamo.exec(operations)
    * ... }
    * List(Right(Transport(Underground,Metropolitan,Magenta)))
    * }}}
    *
    * {{{
    * >>> case class GithubProject(organisation: String, repository: String, language: String, license: String)
    *
    * >>> import org.scanamo.auto._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("organisation" -> S, "repository" -> S)("language" -> S, "license" -> S) { (t, i) =>
    * ...   val githubProjects = Table[Composite, (String, String), GithubProject](t)
    * ...   val operations = for {
    * ...     _ <- githubProjects.putAll(Set(
    * ...       GithubProject("typelevel", "cats", "Scala", "MIT"),
    * ...       GithubProject("localytics", "sbt-dynamodb", "Scala", "MIT"),
    * ...       GithubProject("tpolecat", "tut", "Scala", "MIT"),
    * ...       GithubProject("guardian", "scanamo", "Scala", "Apache 2")
    * ...     ))
    * ...     scalaMIT <- githubProjects.index(i).query("language" -> "Scala" and ("license" -> "MIT"))
    * ...   } yield scalaMIT.toList
    * ...   scanamo.exec(operations)
    * ... }
    * List(Right(GithubProject(typelevel,cats,Scala,MIT)), Right(GithubProject(tpolecat,tut,Scala,MIT)), Right(GithubProject(localytics,sbt-dynamodb,Scala,MIT)))
    * }}}
    */
  def index[KTI <: KeyType, KI](indexName: String): SecondaryIndex[KTI, KI, V] =
    SecondaryIndexWithOptions(name, indexName, ScanamoQueryOptions.default)

  /**
    * Updates an attribute that is not part of the key and returns the updated row
    *
    * To set an attribute:
    *
    * {{{
    * >>> case class Forecast(location: String, weather: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)("location" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val forecast = Table[Simple, String, Forecast](t)
    * ...   val operations = for {
    * ...     _ <- forecast.put(Forecast("London", "Rain"))
    * ...     updated <- forecast.update("location" -> "London", set("weather" -> "Sun"))
    * ...   } yield updated
    * ...   scanamo.exec(operations)
    * ... }
    * Right(Forecast(London,Sun))
    * }}}
    *
    * List attributes can also be appended or prepended to:
    *
    * {{{
    * >>> case class Character(name: String, actors: List[String])
    *
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val characters = Table[Simple, String, Character](t)
    * ...   val operations = for {
    * ...     _ <- characters.put(Character("The Doctor", List("Ecclestone", "Tennant", "Smith")))
    * ...     _ <- characters.update("name" -> "The Doctor", append("actors" -> "Capaldi"))
    * ...     _ <- characters.update("name" -> "The Doctor", prepend("actors" -> "McCoy"))
    * ...     results <- characters.scan()
    * ...   } yield results.toList
    * ...   scanamo.exec(operations)
    * ... }
    * List(Right(Character(The Doctor,List(McCoy, Ecclestone, Tennant, Smith, Capaldi))))
    * }}}
    *
    * Appending or prepending creates the list if it does not yet exist:
    *
    * {{{
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val characters = Table[Simple, String, Character](t)
    * ...   val operations = for {
    * ...     _ <- characters.update("name" -> "James Bond", append("actors" -> "Craig"))
    * ...     results <- characters.query("name" -> "James Bond")
    * ...   } yield results.toList
    * ...   scanamo.exec(operations)
    * ... }
    * List(Right(Character(James Bond,List(Craig))))
    * }}}
    *
    * To concatenate a list to the front or end of an existing list, use appendAll/prependAll:
    *
    * {{{
    * >>> case class Fruit(kind: String, sources: List[String])
    *
    * >>> LocalDynamoDB.withRandomTable(client)("kind" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val fruits = Table[Simple, String, Fruit](t)
    * ...   val operations = for {
    * ...     _ <- fruits.put(Fruit("watermelon", List("USA")))
    * ...     _ <- fruits.update("kind" -> "watermelon", appendAll("sources" -> List("China", "Turkey")))
    * ...     _ <- fruits.update("kind" -> "watermelon", prependAll("sources" -> List("Brazil")))
    * ...     results <- fruits.query("kind" -> "watermelon")
    * ...   } yield results.toList
    * ...   scanamo.exec(operations)
    * ... }
    * List(Right(Fruit(watermelon,List(Brazil, USA, China, Turkey))))
    * }}}
    *
    * Multiple operations can also be performed in one call:
    * {{{
    * >>> case class Foo(name: String, bar: Int, l: List[String])
    *
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val foos = Table[Simple, String, Foo](t)
    * ...   val operations = for {
    * ...     _ <- foos.put(Foo("x", 0, List("First")))
    * ...     updated <- foos.update("name" -> "x",
    * ...       append("l" -> "Second") and set("bar" -> 1))
    * ...   } yield updated
    * ...   scanamo.exec(operations)
    * ... }
    * Right(Foo(x,1,List(First, Second)))
    * }}}
    *
    * It"s" also possible to perform `ADD` and `DELETE` updates
    * {{{
    * >>> case class Bar(name: String, counter: Long, set: Set[String])
    *
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val bars = Table[Simple, String, Bar](t)
    * ...   val operations = for {
    * ...     _ <- bars.put(Bar("x", 1L, Set("First")))
    * ...     _ <- bars.update("name" -> "x",
    * ...       add("counter" -> 10L) and add("set" -> Set("Second")))
    * ...     updatedBar <- bars.update("name" -> "x", delete("set" -> Set("First")))
    * ...   } yield updatedBar
    * ...   scanamo.exec(operations)
    * ... }
    * Right(Bar(x,11,Set(Second)))
    * }}}
    *
    * Updates may occur on nested attributes
    * {{{
    * >>> case class Inner(session: String)
    * >>> case class Middle(name: String, counter: Long, inner: Inner, list: List[Int])
    * >>> case class Outer(id: java.util.UUID, middle: Middle)
    *
    * >>> LocalDynamoDB.withRandomTable(client)("id" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val outers = Table[Simple, java.util.UUID, Outer](t)
    * ...   val id = java.util.UUID.fromString("a8345373-9a93-43be-9bcd-e3682c9197f4")
    * ...   val operations = for {
    * ...     _ <- outers.put(Outer(id, Middle("x", 1L, Inner("alpha"), List(1, 2))))
    * ...     updatedOuter <- outers.update("id" -> id,
    * ...       set("middle" \ "inner" \ "session" -> "beta") and add(("middle" \ "list")(1) ->  1)
    * ...     )
    * ...   } yield updatedOuter
    * ...   scanamo.exec(operations)
    * ... }
    * Right(Outer(a8345373-9a93-43be-9bcd-e3682c9197f4,Middle(x,1,Inner(beta),List(1, 3))))
    * }}}
    *
    * It"s" possible to update one field to the value of another
    * {{{
    * >>> case class Thing(id: String, mandatory: Int, optional: Option[Int])
    *
    * >>> LocalDynamoDB.withRandomTable(client)("id" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val things = Table[Simple, String, Thing](t)
    * ...   val operations = for {
    * ...     _ <- things.put(Thing("a1", 3, None))
    * ...     updated <- things.update("id" -> "a1", set("optional", "mandatory"))
    * ...   } yield updated
    * ...   scanamo.exec(operations)
    * ... }
    * Right(Thing(a1,3,Some(3)))
    * }}}
    */
  def update(key: Key[KT, K], expression: UpdateExpression): ScanamoOps[Either[DynamoReadError, V]] =
    ScanamoFree.update(name)(key)(expression)

  /**
    * Query or scan a table, limiting the number of items evaluated by Dynamo
    * {{{
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val transport = Table[Composite, (String, String), Transport](t)
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central")))
    * ...     results <- transport.limit(1).query("mode" -> "Underground" and ("line" beginsWith "C"))
    * ...   } yield results.toList
    * ...   scanamo.exec(operations)
    * ... }
    * List(Right(Transport(Underground,Central)))
    * }}}
    */
  def limit(n: Int): TableWithOptions[KT, K, V] = TableWithOptions(name, ScanamoQueryOptions.default).limit(n)

  /**
    * Perform strongly consistent (http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html)
    * read operations against this table. Note that there is no equivalent on
    * table indexes as consistent reads from secondary indexes are not
    * supported by DynamoDB
    *
    * {{{
    * >>> case class City(country: String, name: String)
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    * >>> val (get, scan, query) = LocalDynamoDB.withRandomTable(client)("country" -> S, "name" -> S) { t =>
    * ...   import org.scanamo.syntax._
    * ...   import org.scanamo.auto._
    * ...   val cityTable = Table[Composite, (String, String), City](t)
    * ...   val ops = for {
    * ...     _ <- cityTable.putAll(Set(
    * ...       City("US", "Nashville"), City("IT", "Rome"), City("IT", "Siena"), City("TZ", "Dar es Salaam")))
    * ...     get <- cityTable.consistently.get("country" -> "US" && "name" -> "Nashville")
    * ...     scan <- cityTable.consistently.scan()
    * ...     query <- cityTable.consistently.query("country" -> "IT")
    * ...   } yield (get, scan, query)
    * ...   scanamo.exec(ops)
    * ... }
    * >>> get
    * Some(Right(City(US,Nashville)))
    *
    * >>> scan
    * List(Right(City(US,Nashville)), Right(City(IT,Rome)), Right(City(IT,Siena)), Right(City(TZ,Dar es Salaam)))
    *
    * >>> query
    * List(Right(City(IT,Rome)), Right(City(IT,Siena)))
    * }}}
    */
  def consistently: ConsistentlyReadTable[KT, K, V] = ConsistentlyReadTable(name)

  /**
    * Performs the chained operation, `put` if the condition is met
    *
    * {{{
    * >>> case class Farm(animals: List[String], hectares: Int)
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    * >>> import org.scanamo.query._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    *
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   val farmersTable = Table[Simple, String, Farmer](t)
    * ...   val farmerOps = for {
    * ...     _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"), 30)))
    * ...     _ <- farmersTable.given("age" -> 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"), 30)))
    * ...     _ <- farmersTable.given("age" -> 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"), 30)))
    * ...     farmerWithNewStock <- farmersTable.get("name" -> "McDonald")
    * ...   } yield farmerWithNewStock
    * ...   scanamo.exec(farmerOps)
    * ... }
    * Some(Right(Farmer(McDonald,156,Farm(List(sheep, chicken),30))))
    *
    * >>> case class Letter(roman: String, greek: String)
    * >>> LocalDynamoDB.withRandomTable(client)("roman" -> S) { t =>
    * ...   val lettersTable = Table[Simple, String, Letter](t)
    * ...   val ops = for {
    * ...     _ <- lettersTable.putAll(Set(Letter("a", "alpha"), Letter("b", "beta"), Letter("c", "gammon")))
    * ...     _ <- lettersTable.given("greek" beginsWith "ale").put(Letter("a", "aleph"))
    * ...     _ <- lettersTable.given("greek" beginsWith "gam").put(Letter("c", "gamma"))
    * ...     letters <- lettersTable.scan()
    * ...   } yield letters
    * ...   scanamo.exec(ops).toList
    * ... }
    * List(Right(Letter(b,beta)), Right(Letter(c,gamma)), Right(Letter(a,alpha)))
    *
    * >>> import cats.implicits._
    * >>> case class Turnip(size: Int, description: Option[String])
    * >>> LocalDynamoDB.withRandomTable(client)("size" -> N) { t =>
    * ...   val turnipsTable = Table[Simple, Int, Turnip](t)
    * ...   val ops = for {
    * ...     _ <- turnipsTable.putAll(Set(Turnip(1, None), Turnip(1000, None)))
    * ...     initialTurnips <- turnipsTable.scan()
    * ...     _ <- initialTurnips.flatMap(_.toOption).traverse(t =>
    * ...       turnipsTable.given("size" > 500).put(t.copy(description = Some("Big turnip in the country."))))
    * ...     turnips <- turnipsTable.scan()
    * ...   } yield turnips
    * ...   scanamo.exec(ops).toList
    * ... }
    * List(Right(Turnip(1,None)), Right(Turnip(1000,Some(Big turnip in the country.))))
    * }}}
    *
    * Conditions can also make use of negation via `not`:
    *
    * {{{
    * >>> case class Thing(a: String, maybe: Option[Int])
    * >>> LocalDynamoDB.withRandomTable(client)("a" -> S) { t =>
    * ...   val thingTable = Table[Simple, String, Thing](t)
    * ...   val ops = for {
    * ...     _ <- thingTable.putAll(Set(Thing("a", None), Thing("b", Some(1)), Thing("c", None)))
    * ...     _ <- thingTable.given(attributeExists("maybe")).put(Thing("a", Some(2)))
    * ...     _ <- thingTable.given(attributeExists("maybe")).put(Thing("b", Some(3)))
    * ...     _ <- thingTable.given(Not(attributeExists("maybe"))).put(Thing("c", Some(42)))
    * ...     _ <- thingTable.given(Not(attributeExists("maybe"))).put(Thing("b", Some(42)))
    * ...     things <- thingTable.scan()
    * ...   } yield things
    * ...   scanamo.exec(ops).toList
    * ... }
    * List(Right(Thing(b,Some(3))), Right(Thing(c,Some(42))), Right(Thing(a,None)))
    * }}}
    *
    * be combined with `and`
    *
    * {{{
    * >>> case class Compound(a: String, maybe: Option[Int])
    * >>> LocalDynamoDB.withRandomTable(client)("a" -> S) { t =>
    * ...   val compoundTable = Table[Simple, String, Compound](t)
    * ...   val ops = for {
    * ...     _ <- compoundTable.putAll(Set(Compound("alpha", None), Compound("beta", Some(1)), Compound("gamma", None)))
    * ...     _ <- compoundTable.given(Condition(attributeExists("maybe")) and "a" -> "alpha").put(Compound("alpha", Some(2)))
    * ...     _ <- compoundTable.given(Condition(attributeExists("maybe")) and "a" -> "beta").put(Compound("beta", Some(3)))
    * ...     _ <- compoundTable.given(Condition("a" -> "gamma") and attributeExists("maybe")).put(Compound("gamma", Some(42)))
    * ...     compounds <- compoundTable.scan()
    * ...   } yield compounds
    * ...   scanamo.exec(ops).toList
    * ... }
    * List(Right(Compound(beta,Some(3))), Right(Compound(alpha,None)), Right(Compound(gamma,None)))
    * }}}
    *
    * or with `or`
    *
    * {{{
    * >>> case class Choice(number: Int, description: String)
    * >>> LocalDynamoDB.withRandomTable(client)("number" -> N) { t =>
    * ...   val choicesTable = Table[Simple, Int, Choice](t)
    * ...   val ops = for {
    * ...     _ <- choicesTable.putAll(Set(Choice(1, "cake"), Choice(2, "crumble"), Choice(3, "custard")))
    * ...     _ <- choicesTable.given(Condition("description" -> "cake") or Condition("description" -> "death")).put(Choice(1, "victoria sponge"))
    * ...     _ <- choicesTable.given(Condition("description" -> "cake") or Condition("description" -> "death")).put(Choice(2, "victoria sponge"))
    * ...     choices <- choicesTable.scan()
    * ...   } yield choices
    * ...   scanamo.exec(ops).toList
    * ... }
    * List(Right(Choice(2,crumble)), Right(Choice(1,victoria sponge)), Right(Choice(3,custard)))
    * }}}
    *
    * The same forms of condition can be applied to deletions
    *
    * {{{
    * >>> case class Gremlin(number: Int, wet: Boolean, friendly: Boolean)
    * >>> LocalDynamoDB.withRandomTable(client)("number" -> N) { t =>
    * ...   val gremlinsTable = Table[Simple, Int, Gremlin](t)
    * ...   val ops = for {
    * ...     _ <- gremlinsTable.putAll(Set(Gremlin(1, false, true), Gremlin(2, true, false)))
    * ...     _ <- gremlinsTable.given("wet" -> true).delete("number" -> 1)
    * ...     _ <- gremlinsTable.given("wet" -> true).delete("number" -> 2)
    * ...     remainingGremlins <- gremlinsTable.scan()
    * ...   } yield remainingGremlins
    * ...   scanamo.exec(ops).toList
    * ... }
    * List(Right(Gremlin(1,false,true)))
    * }}}
    *
    * and updates
    *
    * {{{
    * >>> LocalDynamoDB.withRandomTable(client)("number" -> N) { t =>
    * ...   val gremlinsTable = Table[Simple, Int, Gremlin](t)
    * ...   val ops = for {
    * ...     _ <- gremlinsTable.putAll(Set(Gremlin(1, false, true), Gremlin(2, true, true)))
    * ...     _ <- gremlinsTable.given("wet" -> true).update("number" -> 1, set("friendly" -> false))
    * ...     _ <- gremlinsTable.given("wet" -> true).update("number" -> 2, set("friendly" -> false))
    * ...     remainingGremlins <- gremlinsTable.scan()
    * ...   } yield remainingGremlins
    * ...   scanamo.exec(ops).toList
    * ... }
    * List(Right(Gremlin(2,true,false)), Right(Gremlin(1,false,true)))
    * }}}
    *
    * Conditions can also be placed on nested attributes
    *
    * {{{
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   val smallscaleFarmersTable = Table[Simple, String, Farmer](t)
    * ...   val farmerOps = for {
    * ...     _ <- smallscaleFarmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"), 30)))
    * ...     _ <- smallscaleFarmersTable.given("farm" \ "hectares" < 40L).put(Farmer("McDonald", 156L, Farm(List("gerbil", "hamster"), 20)))
    * ...     _ <- smallscaleFarmersTable.given("farm" \ "hectares" > 40L).put(Farmer("McDonald", 156L, Farm(List("elephant"), 50)))
    * ...     _ <- smallscaleFarmersTable.given("farm" \ "hectares" -> 20L).update("name" -> "McDonald", append("farm" \ "animals" -> "squirrel"))
    * ...     farmerWithNewStock <- smallscaleFarmersTable.get("name" -> "McDonald")
    * ...   } yield farmerWithNewStock
    * ...   scanamo.exec(farmerOps)
    * ... }
    * Some(Right(Farmer(McDonald,156,Farm(List(gerbil, hamster, squirrel),20))))
    * }}}
    */
  def given[T: ConditionExpression](condition: T): ConditionalOperation[KT, K, V, T] =
    ConditionalOperation(name, condition)

  /**
    * Primes a search request with a key to start from:
    *
    * {{{
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    *
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   val table = Table[Simple, String, Bear](t)
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey"))
    * ...     _ <- table.put(Bear("Baloo", "ants"))
    * ...     _ <- table.put(Bear("Yogi", "picnic baskets"))
    * ...     bears <- table.from("name" -> "Baloo").scan()
    * ...   } yield bears
    * ...   scanamo.exec(ops)
    * ... }
    * List(Right(Bear(Pooh,honey)), Right(Bear(Yogi,picnic baskets)))
    * }}}
    *
    * Of course it works with queries too:
    *
    * {{{
    * >>> case class Event(`type`: String, tag: String, count: Int)
    *
    * >>> LocalDynamoDB.withRandomTable(client)("type" -> S, "tag" -> S) { t =>
    * ...   val table = Table[Composite, (String, String), Event](t)
    * ...   val ops = for {
    * ...     _ <- table.putAll(Set(
    * ...            Event("click", "paid", 600),
    * ...            Event("play", "profile", 100),
    * ...            Event("play", "politics", 200),
    * ...            Event("click", "profile", 400),
    * ...            Event("play", "print", 600),
    * ...            Event("click", "print", 300),
    * ...            Event("play", "paid", 900)
    * ...          ))
    * ...     events <- table.from("type" -> "play" && "tag" -> "politics").query("type" -> "play" and ("tag" beginsWith "p"))
    * ...   } yield events
    * ...   scanamo.exec(ops)
    * ... }
    * List(Right(Event(play,print,600)), Right(Event(play,profile,100)))
    * }}}
    *
    */
  def from(key: Key[KT, K]): TableWithOptions[KT, K, V] = TableWithOptions(name, ScanamoQueryOptions.default).from(key)

  /**
    * Scans all elements of a table
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   import org.scanamo.auto._
    * ...   val table = Table[Simple, String, Bear](t)
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey"))
    * ...     _ <- table.put(Bear("Yogi", "picnic baskets"))
    * ...     bears <- table.scan()
    * ...   } yield bears
    * ...   scanamo.exec(ops)
    * ... }
    * List(Right(Bear(Pooh,honey)), Right(Bear(Yogi,picnic baskets)))
    * }}}
    */
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] = ScanamoFree.scan[V](name)

  /**
    * Scans the table and returns the raw DynamoDB result. Sometimes, one might want to
    * access metadata returned in the `ScanResult` object, such as the last evaluated
    * key for example. `Table#scan` only returns a list of results, so there is no
    * place for putting that information: this is where `scan0` comes in handy!
    *
    * A particular use case is when one wants to paginate through result sets, say:
    * {{{
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    *
    * >>> import cats.implicits._
    * >>> import org.scanamo.error._
    * >>> import org.scanamo.ops._
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    * >>> import org.scanamo.query._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
    * ...   val table = Table[Composite, (String, String), Transport](t)
    * ...   val ops = for {
    * ...     _ <- table.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central")
    * ...     ))
    * ...     res <- table.limit(1).scan0
    * ...     lastKey = Key.fromDynamoObject[String, String](("mode", "line"), DynamoObject(res.getLastEvaluatedKey))
    * ...     ts <- lastKey.fold(List.empty[Either[DynamoReadError, Transport]].pure[ScanamoOps])(table.from(_).scan())
    * ...   } yield ts
    * ...   scanamo.exec(ops)
    * ... }
    * List(Right(Transport(Underground,Circle)), Right(Transport(Underground,Metropolitan)))
    * }}}
    */
  def scan0: ScanamoOps[ScanResult] = ScanamoFree.scan0[V](name)

  /**
    * Query a table based on the hash key and optionally the range key
    *
    * {{{
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    *
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
    * ...   val table = Table[Composite, (String, String), Transport](t)
    * ...   val ops = for {
    * ...     _ <- table.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central")
    * ...     ))
    * ...     linesBeginningWithC <- table.query("mode" -> "Underground" and ("line" beginsWith "C"))
    * ...   } yield linesBeginningWithC
    * ...   scanamo.exec(ops)
    * ... }
    * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
    * }}}
    */
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] = ScanamoFree.query[V](name)(query)

  /**
    * Queries the table and returns the raw DynamoDB result. Sometimes, one might want to
    * access metadata returned in the `QueryResult` object, such as the last evaluated
    * key for example. `Table#query` only returns a list of results, so there is no
    * place for putting that information: this is where `query0` comes in handy!
    *
    * A particular use case is when one wants to paginate through result sets, say:
    * {{{
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    *
    * >>> import cats.implicits._
    * >>> import org.scanamo.error._
    * >>> import org.scanamo.ops._
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    * >>> import org.scanamo.query._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)("mode" -> S, "line" -> S) { t =>
    * ...   val table = Table[Composite, (String, String), Transport](t)
    * ...   val ops = for {
    * ...     _ <- table.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central"),
    * ...       Transport("Bus", "390"),
    * ...       Transport("Bus", "143"),
    * ...       Transport("Bus", "234")
    * ...     ))
    * ...     res <- table.limit(1).query0("mode" -> "Bus" and "line" -> "234")
    * ...     lastKey = Key.fromDynamoObject[String, String](("mode", "line"), DynamoObject(res.getLastEvaluatedKey))
    * ...     ts <- lastKey.fold(List.empty[Either[DynamoReadError, Transport]].pure[ScanamoOps])(table.from(_).scan())
    * ...   } yield ts
    * ...   scanamo.exec(ops)
    * ... }
    * List(Right(Transport(Bus,390)), Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)), Right(Transport(Underground,Metropolitan)))
    * }}}
    */
  def query0(query: Query[_]): ScanamoOps[QueryResult] = ScanamoFree.query0[V](name)(query)

  /**
    * Filter the results of a Scan or Query
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String, antagonist: Option[String])
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = Scanamo(client)
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    *
    * >>> LocalDynamoDB.withRandomTable(client)("name" -> S) { t =>
    * ...   val table = Table[Simple, String, Bear](t)
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey", None))
    * ...     _ <- table.put(Bear("Yogi", "picnic baskets", Some("Ranger Smith")))
    * ...     honeyBears <- table.filter("favouriteFood" -> "honey").scan()
    * ...     competitiveBears <- table.filter(attributeExists("antagonist")).scan()
    * ...   } yield (honeyBears, competitiveBears)
    * ...   scanamo.exec(ops)
    * ... }
    * (List(Right(Bear(Pooh,honey,None))),List(Right(Bear(Yogi,picnic baskets,Some(Ranger Smith)))))
    *
    * >>> case class Station(line: String, name: String, zone: Int)
    *
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)("line" -> S, "name" -> S) { t =>
    * ...   val stationTable = Table[Composite, (String, String), Station](t)
    * ...   val ops = for {
    * ...     _ <- stationTable.putAll(Set(
    * ...       Station("Metropolitan", "Chalfont & Latimer", 8),
    * ...       Station("Metropolitan", "Chorleywood", 7),
    * ...       Station("Metropolitan", "Rickmansworth", 7),
    * ...       Station("Metropolitan", "Croxley", 7),
    * ...       Station("Jubilee", "Canons Park", 5)
    * ...     ))
    * ...     filteredStations <- stationTable.filter("zone" -> Set(8, 7)).query("line" -> "Metropolitan" and ("name" beginsWith "C"))
    * ...   } yield filteredStations
    * ...   scanamo.exec(ops)
    * ... }
    * List(Right(Station(Metropolitan,Chalfont & Latimer,8)), Right(Station(Metropolitan,Chorleywood,7)), Right(Station(Metropolitan,Croxley,7)))
    * }}}
    */
  def filter[C: ConditionExpression](condition: C): TableWithOptions[KT, K, V] =
    TableWithOptions(name, ScanamoQueryOptions.default).filter(Condition(condition))

  def descending: TableWithOptions[KT, K, V] =
    TableWithOptions(name, ScanamoQueryOptions.default).descending
}

private[scanamo] case class ConsistentlyReadTable[KT <: KeyType, K, V: DynamoFormat](tableName: String) {
  def limit(n: Int): TableWithOptions[KT, K, V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.limit(n)
  def descending: TableWithOptions[KT, K, V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.descending
  def from(key: Key[KT, K]): TableWithOptions[KT, K, V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.from(key)
  def filter[T](c: Condition[T]): TableWithOptions[KT, K, V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.filter(c)
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.scan()
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.query(query)

  def get(key: Key[KT, K]): ScanamoOps[Option[Either[DynamoReadError, V]]] =
    ScanamoFree.get(tableName)(key, true)
  def getAll(keys: Iterable[Key[KT, K]]): ScanamoOps[Set[Either[DynamoReadError, V]]] =
    ScanamoFree.getAll(tableName)(keys, true)
}

private[scanamo] case class TableWithOptions[KT <: KeyType, K, V: DynamoFormat](tableName: String,
                                                                                queryOptions: ScanamoQueryOptions) {
  def limit(n: Int): TableWithOptions[KT, K, V] = copy(queryOptions = queryOptions.copy(limit = Some(n)))
  def consistently: TableWithOptions[KT, K, V] = copy(queryOptions = queryOptions.copy(consistent = true))
  def descending: TableWithOptions[KT, K, V] = copy(queryOptions = queryOptions.copy(ascending = false))
  def from(key: Key[KT, K]): TableWithOptions[KT, K, V] =
    copy(queryOptions = queryOptions.copy(exclusiveStartKey = Some(key.toDynamoObject)))
  def filter[T](c: Condition[T]): TableWithOptions[KT, K, V] =
    copy(queryOptions = queryOptions.copy(filter = Some(c)))

  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] =
    ScanResultStream.stream[V](ScanamoScanRequest(tableName, None, queryOptions)).map(_._1)
  def scan0: ScanamoOps[ScanResult] =
    ScanamoOps.scan(ScanamoScanRequest(tableName, None, queryOptions))
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] =
    QueryResultStream.stream[V](ScanamoQueryRequest(tableName, None, query, queryOptions)).map(_._1)
  def query0(query: Query[_]): ScanamoOps[QueryResult] =
    ScanamoOps.query(ScanamoQueryRequest(tableName, None, query, queryOptions))
}
