package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, DeleteItemResult}
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query._
import com.gu.scanamo.request.{ScanamoQueryOptions, ScanamoQueryRequest, ScanamoScanRequest}
import com.gu.scanamo.update.UpdateExpression
import scala.collection.JavaConverters._

/**
  * Represents a DynamoDB table that operations can be performed against
  *
  * {{{
  * >>> case class Transport(mode: String, line: String)
  *
  * >>> val client = LocalDynamoDB.client()
  * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
  *
  * >>> LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
  * ...   import com.gu.scanamo.syntax._
  * ...   val transport = Table[Transport](t)
  * ...   val operations = for {
  * ...     _ <- transport.putAll(Set(
  * ...       Transport("Underground", "Circle"),
  * ...       Transport("Underground", "Metropolitan"),
  * ...       Transport("Underground", "Central")))
  * ...     results <- transport.query('mode -> "Underground" and ('line beginsWith "C"))
  * ...   } yield results.toList
  * ...   Scanamo.exec(client)(operations)
  * ... }
  * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
  * }}}
  */
case class Table[V: DynamoFormat](name: String) {

  def put(v: V): ScanamoOps[Option[Either[DynamoReadError, V]]] = ScanamoFree.put(name)(v)
  def putAll(vs: Set[V]): ScanamoOps[List[BatchWriteItemResult]] = ScanamoFree.putAll(name)(vs)
  def get(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]] = ScanamoFree.get[V](name)(key)
  def getAll(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, V]]] = ScanamoFree.getAll[V](name)(keys)
  def delete(key: UniqueKey[_]): ScanamoOps[DeleteItemResult] = ScanamoFree.delete(name)(key)

  /**
    * Deletes multiple items by a unique key
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> val dataSet = Set(
    * ...   Farmer("Patty", 200L, Farm(List("unicorn"))),
    * ...   Farmer("Ted", 40L, Farm(List("T-Rex"))),
    * ...   Farmer("Jack", 2L, Farm(List("velociraptor"))))
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   val farm = Table[Farmer](t)
    * ...   val operations = for {
    * ...     _       <- farm.putAll(dataSet)
    * ...     _       <- farm.deleteAll('name -> dataSet.map(_.name))
    * ...     scanned <- farm.scan
    * ...   } yield scanned.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List()
    * }}}
    */
  def deleteAll(items: UniqueKeys[_]): ScanamoOps[List[BatchWriteItemResult]] = ScanamoFree.deleteAll(name)(items)

  /**
    * A secondary index on the table which can be scanned, or queried against
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('mode -> S, 'line -> S)('colour -> S) { (t, i) =>
    * ...   val transport = Table[Transport](t)
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle", "Yellow"),
    * ...       Transport("Underground", "Metropolitan", "Magenta"),
    * ...       Transport("Underground", "Central", "Red")))
    * ...     MagentaLine <- transport.index(i).query('colour -> "Magenta")
    * ...   } yield MagentaLine.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Metropolitan,Magenta)))
    * }}}
    *
    * {{{
    * >>> case class GithubProject(organisation: String, repository: String, language: String, license: String)
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('organisation -> S, 'repository -> S)('language -> S, 'license -> S) { (t, i) =>
    * ...   val githubProjects = Table[GithubProject](t)
    * ...   val operations = for {
    * ...     _ <- githubProjects.putAll(Set(
    * ...       GithubProject("typelevel", "cats", "Scala", "MIT"),
    * ...       GithubProject("localytics", "sbt-dynamodb", "Scala", "MIT"),
    * ...       GithubProject("tpolecat", "tut", "Scala", "MIT"),
    * ...       GithubProject("guardian", "scanamo", "Scala", "Apache 2")
    * ...     ))
    * ...     scalaMIT <- githubProjects.index(i).query('language -> "Scala" and ('license -> "MIT"))
    * ...   } yield scalaMIT.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(GithubProject(typelevel,cats,Scala,MIT)), Right(GithubProject(tpolecat,tut,Scala,MIT)), Right(GithubProject(localytics,sbt-dynamodb,Scala,MIT)))
    * }}}
    */
  def index(indexName: String): SecondaryIndex[V] =
    SecondaryIndexWithOptions[V](name, indexName, ScanamoQueryOptions.default)

  /**
    * Updates an attribute that is not part of the key and returns the updated row
    *
    * To set an attribute:
    *
    * {{{
    * >>> case class Forecast(location: String, weather: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('location -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val forecast = Table[Forecast](t)
    * ...   val operations = for {
    * ...     _ <- forecast.put(Forecast("London", "Rain"))
    * ...     updated <- forecast.update('location -> "London", set('weather -> "Sun"))
    * ...   } yield updated
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * Right(Forecast(London,Sun))
    * }}}
    *
    * List attributes can also be appended or prepended to:
    *
    * {{{
    * >>> case class Character(name: String, actors: List[String])
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val characters = Table[Character](t)
    * ...   val operations = for {
    * ...     _ <- characters.put(Character("The Doctor", List("Ecclestone", "Tennant", "Smith")))
    * ...     _ <- characters.update('name -> "The Doctor", append('actors -> "Capaldi"))
    * ...     _ <- characters.update('name -> "The Doctor", prepend('actors -> "McCoy"))
    * ...     results <- characters.scan()
    * ...   } yield results.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Character(The Doctor,List(McCoy, Ecclestone, Tennant, Smith, Capaldi))))
    * }}}
    *
    * Appending or prepending creates the list if it does not yet exist:
    *
    * {{{
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val characters = Table[Character](t)
    * ...   val operations = for {
    * ...     _ <- characters.update('name -> "James Bond", append('actors -> "Craig"))
    * ...     results <- characters.query('name -> "James Bond")
    * ...   } yield results.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Character(James Bond,List(Craig))))
    * }}}
    *
    * To concatenate a list to the front or end of an existing list, use appendAll/prependAll:
    *
    * {{{
    * >>> case class Fruit(kind: String, sources: List[String])
    *
    * >>> LocalDynamoDB.withRandomTable(client)('kind -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val fruits = Table[Fruit](t)
    * ...   val operations = for {
    * ...     _ <- fruits.put(Fruit("watermelon", List("USA")))
    * ...     _ <- fruits.update('kind -> "watermelon", appendAll('sources -> List("China", "Turkey")))
    * ...     _ <- fruits.update('kind -> "watermelon", prependAll('sources -> List("Brazil")))
    * ...     results <- fruits.query('kind -> "watermelon")
    * ...   } yield results.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Fruit(watermelon,List(Brazil, USA, China, Turkey))))
    * }}}
    *
    * Multiple operations can also be performed in one call:
    * {{{
    * >>> case class Foo(name: String, bar: Int, l: List[String])
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val foos = Table[Foo](t)
    * ...   val operations = for {
    * ...     _ <- foos.put(Foo("x", 0, List("First")))
    * ...     updated <- foos.update('name -> "x",
    * ...       append('l -> "Second") and set('bar -> 1))
    * ...   } yield updated
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * Right(Foo(x,1,List(First, Second)))
    * }}}
    *
    * It's also possible to perform `ADD` and `DELETE` updates
    * {{{
    * >>> case class Bar(name: String, counter: Long, set: Set[String])
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val bars = Table[Bar](t)
    * ...   val operations = for {
    * ...     _ <- bars.put(Bar("x", 1L, Set("First")))
    * ...     _ <- bars.update('name -> "x",
    * ...       add('counter -> 10L) and add('set -> Set("Second")))
    * ...     updatedBar <- bars.update('name -> "x", delete('set -> Set("First")))
    * ...   } yield updatedBar
    * ...   Scanamo.exec(client)(operations)
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
    * >>> LocalDynamoDB.withRandomTable(client)('id -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val outers = Table[Outer](t)
    * ...   val id = java.util.UUID.fromString("a8345373-9a93-43be-9bcd-e3682c9197f4")
    * ...   val operations = for {
    * ...     _ <- outers.put(Outer(id, Middle("x", 1L, Inner("alpha"), List(1, 2))))
    * ...     updatedOuter <- outers.update('id -> id,
    * ...       set('middle \ 'inner \ 'session -> "beta") and add(('middle \ 'list)(1) ->  1)
    * ...     )
    * ...   } yield updatedOuter
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * Right(Outer(a8345373-9a93-43be-9bcd-e3682c9197f4,Middle(x,1,Inner(beta),List(1, 3))))
    * }}}
    *
    * It's possible to update one field to the value of another
    * {{{
    * >>> case class Thing(id: String, mandatory: Int, optional: Option[Int])
    *
    * >>> LocalDynamoDB.withRandomTable(client)('id -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val things = Table[Thing](t)
    * ...   val operations = for {
    * ...     _ <- things.put(Thing("a1", 3, None))
    * ...     updated <- things.update('id -> "a1", set('optional -> 'mandatory))
    * ...   } yield updated
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * Right(Thing(a1,3,Some(3)))
    * }}}
    */
  def update(key: UniqueKey[_], expression: UpdateExpression): ScanamoOps[Either[DynamoReadError, V]] =
    ScanamoFree.update[V](name)(key)(expression)

  /**
    * Query or scan a table, limiting the number of items evaluated by Dynamo
    * {{{
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val transport = Table[Transport](t)
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central")))
    * ...     results <- transport.limit(1).query('mode -> "Underground" and ('line beginsWith "C"))
    * ...   } yield results.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Central)))
    * }}}
    */
  def limit(n: Int) = TableWithOptions[V](name, ScanamoQueryOptions.default).limit(n)

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
    * >>> val (get, scan, query) = LocalDynamoDB.withRandomTable(client)('country -> S, 'name -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   val cityTable = Table[City](t)
    * ...   val ops = for {
    * ...     putRes <- cityTable.putAll(Set(
    * ...       City("US", "Nashville"), City("IT", "Rome"), City("IT", "Siena"), City("TZ", "Dar es Salaam")))
    * ...     get <- cityTable.consistently.get('country -> "US" and 'name -> "Nashville")
    * ...     scan <- cityTable.consistently.scan()
    * ...     query <- cityTable.consistently.query('country -> "IT")
    * ...   } yield (get, scan, query)
    * ...   Scanamo.exec(client)(ops)
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
  def consistently = ConsistentlyReadTable(name)

  /**
    * Performs the chained operation, `put` if the condition is met
    *
    * {{{
    * >>> case class Farm(animals: List[String], hectares: Int)
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.gu.scanamo.query._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   val farmersTable = Table[Farmer](t)
    * ...   val farmerOps = for {
    * ...     _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"), 30)))
    * ...     _ <- farmersTable.given('age -> 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"), 30)))
    * ...     _ <- farmersTable.given('age -> 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"), 30)))
    * ...     farmerWithNewStock <- farmersTable.get('name -> "McDonald")
    * ...   } yield farmerWithNewStock
    * ...   Scanamo.exec(client)(farmerOps)
    * ... }
    * Some(Right(Farmer(McDonald,156,Farm(List(sheep, chicken),30))))
    *
    * >>> case class Letter(roman: String, greek: String)
    * >>> LocalDynamoDB.withRandomTable(client)('roman -> S) { t =>
    * ...   val lettersTable = Table[Letter](t)
    * ...   val ops = for {
    * ...     _ <- lettersTable.putAll(Set(Letter("a", "alpha"), Letter("b", "beta"), Letter("c", "gammon")))
    * ...     _ <- lettersTable.given('greek beginsWith "ale").put(Letter("a", "aleph"))
    * ...     _ <- lettersTable.given('greek beginsWith "gam").put(Letter("c", "gamma"))
    * ...     letters <- lettersTable.scan()
    * ...   } yield letters
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Letter(b,beta)), Right(Letter(c,gamma)), Right(Letter(a,alpha)))
    *
    * >>> import cats.implicits._
    * >>> case class Turnip(size: Int, description: Option[String])
    * >>> LocalDynamoDB.withRandomTable(client)('size -> N) { t =>
    * ...   val turnipsTable = Table[Turnip](t)
    * ...   val ops = for {
    * ...     _ <- turnipsTable.putAll(Set(Turnip(1, None), Turnip(1000, None)))
    * ...     initialTurnips <- turnipsTable.scan()
    * ...     _ <- initialTurnips.flatMap(_.toOption).traverse(t =>
    * ...       turnipsTable.given('size > 500).put(t.copy(description = Some("Big turnip in the country."))))
    * ...     turnips <- turnipsTable.scan()
    * ...   } yield turnips
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Turnip(1,None)), Right(Turnip(1000,Some(Big turnip in the country.))))
    * }}}
    *
    * Conditions can also make use of negation via `not`:
    *
    * {{{
    * >>> case class Thing(a: String, maybe: Option[Int])
    * >>> LocalDynamoDB.withRandomTable(client)('a -> S) { t =>
    * ...   val thingTable = Table[Thing](t)
    * ...   val ops = for {
    * ...     _ <- thingTable.putAll(Set(Thing("a", None), Thing("b", Some(1)), Thing("c", None)))
    * ...     _ <- thingTable.given(attributeExists('maybe)).put(Thing("a", Some(2)))
    * ...     _ <- thingTable.given(attributeExists('maybe)).put(Thing("b", Some(3)))
    * ...     _ <- thingTable.given(Not(attributeExists('maybe))).put(Thing("c", Some(42)))
    * ...     _ <- thingTable.given(Not(attributeExists('maybe))).put(Thing("b", Some(42)))
    * ...     things <- thingTable.scan()
    * ...   } yield things
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Thing(b,Some(3))), Right(Thing(c,Some(42))), Right(Thing(a,None)))
    * }}}
    *
    * be combined with `and`
    *
    * {{{
    * >>> case class Compound(a: String, maybe: Option[Int])
    * >>> LocalDynamoDB.withRandomTable(client)('a -> S) { t =>
    * ...   val compoundTable = Table[Compound](t)
    * ...   val ops = for {
    * ...     _ <- compoundTable.putAll(Set(Compound("alpha", None), Compound("beta", Some(1)), Compound("gamma", None)))
    * ...     _ <- compoundTable.given(attributeExists('maybe) and 'a -> "alpha").put(Compound("alpha", Some(2)))
    * ...     _ <- compoundTable.given(attributeExists('maybe) and 'a -> "beta").put(Compound("beta", Some(3)))
    * ...     _ <- compoundTable.given(Condition('a -> "gamma") and attributeExists('maybe)).put(Compound("gamma", Some(42)))
    * ...     compounds <- compoundTable.scan()
    * ...   } yield compounds
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Compound(beta,Some(3))), Right(Compound(alpha,None)), Right(Compound(gamma,None)))
    * }}}
    *
    * or with `or`
    *
    * {{{
    * >>> case class Choice(number: Int, description: String)
    * >>> LocalDynamoDB.withRandomTable(client)('number -> N) { t =>
    * ...   val choicesTable = Table[Choice](t)
    * ...   val ops = for {
    * ...     _ <- choicesTable.putAll(Set(Choice(1, "cake"), Choice(2, "crumble"), Choice(3, "custard")))
    * ...     _ <- choicesTable.given(Condition('description -> "cake") or Condition('description -> "death")).put(Choice(1, "victoria sponge"))
    * ...     _ <- choicesTable.given(Condition('description -> "cake") or Condition('description -> "death")).put(Choice(2, "victoria sponge"))
    * ...     choices <- choicesTable.scan()
    * ...   } yield choices
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Choice(2,crumble)), Right(Choice(1,victoria sponge)), Right(Choice(3,custard)))
    * }}}
    *
    * The same forms of condition can be applied to deletions
    *
    * {{{
    * >>> case class Gremlin(number: Int, wet: Boolean, friendly: Boolean)
    * >>> LocalDynamoDB.withRandomTable(client)('number -> N) { t =>
    * ...   val gremlinsTable = Table[Gremlin](t)
    * ...   val ops = for {
    * ...     _ <- gremlinsTable.putAll(Set(Gremlin(1, false, true), Gremlin(2, true, false)))
    * ...     _ <- gremlinsTable.given('wet -> true).delete('number -> 1)
    * ...     _ <- gremlinsTable.given('wet -> true).delete('number -> 2)
    * ...     remainingGremlins <- gremlinsTable.scan()
    * ...   } yield remainingGremlins
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Gremlin(1,false,true)))
    * }}}
    *
    * and updates
    *
    * {{{
    * >>> LocalDynamoDB.withRandomTable(client)('number -> N) { t =>
    * ...   val gremlinsTable = Table[Gremlin](t)
    * ...   val ops = for {
    * ...     _ <- gremlinsTable.putAll(Set(Gremlin(1, false, true), Gremlin(2, true, true)))
    * ...     _ <- gremlinsTable.given('wet -> true).update('number -> 1, set('friendly -> false))
    * ...     _ <- gremlinsTable.given('wet -> true).update('number -> 2, set('friendly -> false))
    * ...     remainingGremlins <- gremlinsTable.scan()
    * ...   } yield remainingGremlins
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Gremlin(2,true,false)), Right(Gremlin(1,false,true)))
    * }}}
    *
    * Conditions can also be placed on nested attributes
    *
    * {{{
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   val smallscaleFarmersTable = Table[Farmer](t)
    * ...   val farmerOps = for {
    * ...     _ <- smallscaleFarmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"), 30)))
    * ...     _ <- smallscaleFarmersTable.given('farm \ 'hectares < 40L).put(Farmer("McDonald", 156L, Farm(List("gerbil", "hamster"), 20)))
    * ...     _ <- smallscaleFarmersTable.given('farm \ 'hectares > 40L).put(Farmer("McDonald", 156L, Farm(List("elephant"), 50)))
    * ...     _ <- smallscaleFarmersTable.given('farm \ 'hectares -> 20L).update('name -> "McDonald", append('farm \ 'animals -> "squirrel"))
    * ...     farmerWithNewStock <- smallscaleFarmersTable.get('name -> "McDonald")
    * ...   } yield farmerWithNewStock
    * ...   Scanamo.exec(client)(farmerOps)
    * ... }
    * Some(Right(Farmer(McDonald,156,Farm(List(gerbil, hamster, squirrel),20))))
    * }}}
    */
  def given[T: ConditionExpression](condition: T) = ConditionalOperation[V, T](name, condition)

  def from[K: UniqueKeyCondition](key: UniqueKey[K]) = TableWithOptions(name, ScanamoQueryOptions.default).from(key)

  /**
    * Scans all elements of a table
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   val table = Table[Bear](t)
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey"))
    * ...     _ <- table.put(Bear("Yogi", "picnic baskets"))
    * ...     bears <- table.scan()
    * ...   } yield bears
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * List(Right(Bear(Pooh,honey)), Right(Bear(Yogi,picnic baskets)))
    * }}}
    */
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] = ScanamoFree.scan[V](name)

  /**
    * Scans all elements of a table starting from the specified key
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   val table = Table[Bear](t)
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey"))
    * ...     _ <- table.put(Bear("Paddington", "picnic baskets"))
    * ...     _ <- table.put(Bear("Baloo", "ants"))
    * ...     _ <- table.put(Bear("Iorek Byrnison", "seal"))
    * ...     _ <- table.put(Bear("Rupert Bear", "tuna sandwich"))
    * ...     bears <- table.scanFrom('name -> "Paddington")
    * ...   } yield bears._1
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * List(Right(Bear(Baloo,ants)), Right(Bear(Pooh,honey)), Right(Bear(Iorek Byrnison,seal)), Right(Bear(Rupert Bear,tuna sandwich)))
    * }}}
    */
  def scanFrom[K: UniqueKeyCondition](
    key: UniqueKey[K]
  ): ScanamoOps[(List[Either[DynamoReadError, V]], Option[UniqueKey[K]])] =
    TableWithOptions(name, ScanamoQueryOptions.default).scanFrom(key)

  /**
    * Query a table based on the hash key and optionally the range key
    *
    * {{{
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
    * ...   val table = Table[Transport](t)
    * ...   val ops = for {
    * ...     _ <- table.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central")
    * ...     ))
    * ...     linesBeginningWithC <- table.query('mode -> "Underground" and ('line beginsWith "C"))
    * ...   } yield linesBeginningWithC
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
    * }}}
    */
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] = ScanamoFree.query[V](name)(query)

  /**
    * Query a table based on the hash key and optionally the range key, starting from the specified key
    *
    * {{{
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
    * ...   val table = Table[Transport](t)
    * ...   val ops = for {
    * ...     _ <- table.putAll(Set(
    * ...       Transport("Bus", "143"),
    * ...       Transport("Bus", "390"),
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Victorias"),
    * ...       Transport("Underground", "Central")
    * ...     ))
    * ...     linesBeginningWithC <- table.queryFrom('mode -> "Underground", 'mode -> "Underground" and 'line -> "Metropolitan")
    * ...   } yield linesBeginningWithC._1
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * List(Right(Transport(Underground,Victorias)))
    * }}}
    */
  def queryFrom[K: UniqueKeyCondition](
    query: Query[_],
    key: UniqueKey[K]
  ): ScanamoOps[(List[Either[DynamoReadError, V]], Option[UniqueKey[K]])] =
    TableWithOptions(name, ScanamoQueryOptions.default).queryFrom(query, key)

  /**
    * Filter the results of a Scan or Query
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String, antagonist: Option[String])
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   val table = Table[Bear](t)
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey", None))
    * ...     _ <- table.put(Bear("Yogi", "picnic baskets", Some("Ranger Smith")))
    * ...     honeyBears <- table.filter('favouriteFood -> "honey").scan()
    * ...     competitiveBears <- table.filter(attributeExists('antagonist)).scan()
    * ...   } yield (honeyBears, competitiveBears)
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * (List(Right(Bear(Pooh,honey,None))),List(Right(Bear(Yogi,picnic baskets,Some(Ranger Smith)))))
    *
    * >>> case class Station(line: String, name: String, zone: Int)
    *
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('line -> S, 'name -> S) { t =>
    * ...   val stationTable = Table[Station](t)
    * ...   val ops = for {
    * ...     _ <- stationTable.putAll(Set(
    * ...       Station("Metropolitan", "Chalfont & Latimer", 8),
    * ...       Station("Metropolitan", "Chorleywood", 7),
    * ...       Station("Metropolitan", "Rickmansworth", 7),
    * ...       Station("Metropolitan", "Croxley", 7),
    * ...       Station("Jubilee", "Canons Park", 5)
    * ...     ))
    * ...     filteredStations <- stationTable.filter('zone -> Set(8, 7)).query('line -> "Metropolitan" and ('name beginsWith "C"))
    * ...   } yield filteredStations
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * List(Right(Station(Metropolitan,Chalfont & Latimer,8)), Right(Station(Metropolitan,Chorleywood,7)), Right(Station(Metropolitan,Croxley,7)))
    * }}}
    */
  def filter[C: ConditionExpression](condition: C) =
    TableWithOptions(name, ScanamoQueryOptions.default).filter(Condition(condition))
}

private[scanamo] case class ConsistentlyReadTable[V: DynamoFormat](tableName: String) {
  def limit(n: Int): TableWithOptions[V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.limit(n)
  def from[K: UniqueKeyCondition](key: UniqueKey[K]) = 
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.from(key)
  def filter[T](c: Condition[T]): TableWithOptions[V] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.filter(c)

  def get(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]] =
    ScanamoFree.getWithConsistency[V](tableName)(key)
  def getAll(keys: UniqueKeys[_]): ScanamoOps[Set[Either[DynamoReadError, V]]] =
    ScanamoFree.getAllWithConsistency[V](tableName)(keys)
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] =
    ScanamoFree.scanConsistent[V](tableName)
  def scanFrom[K: UniqueKeyCondition](
    key: UniqueKey[K]
  ): ScanamoOps[(List[Either[DynamoReadError, V]], Option[UniqueKey[K]])] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.scanFrom(key)
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] =
    ScanamoFree.queryConsistent[V](tableName)(query)
  def queryFrom[K: UniqueKeyCondition](
    query: Query[_],
    key: UniqueKey[K]
  ): ScanamoOps[(List[Either[DynamoReadError, V]], Option[UniqueKey[K]])] =
    TableWithOptions(tableName, ScanamoQueryOptions.default).consistently.queryFrom(query, key)
}

private[scanamo] case class TableWithOptions[V: DynamoFormat](tableName: String, queryOptions: ScanamoQueryOptions) {
  def limit(n: Int): TableWithOptions[V] = copy(queryOptions = queryOptions.copy(limit = Some(n)))
  def consistently: TableWithOptions[V] = copy(queryOptions = queryOptions.copy(consistent = true))
  def from[K: UniqueKeyCondition](key: UniqueKey[K]) = copy(queryOptions = queryOptions.copy(exclusiveStartKey = Some(key.asAVMap.asJava)))
  def filter[T](c: Condition[T]): TableWithOptions[V] = copy(queryOptions = queryOptions.copy(filter = Some(c)))

  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]] =
    ScanResultStream.stream[V](ScanamoScanRequest(tableName, None, queryOptions)).map(_._1)
  def scanFrom[K](
    key: UniqueKey[K]
  )(implicit K: UniqueKeyCondition[K]): ScanamoOps[(List[Either[DynamoReadError, V]], Option[UniqueKey[K]])] =
    ScanResultStream
      .stream[V](ScanamoScanRequest(tableName, None, queryOptions.copy(exclusiveStartKey = Some(key.asAVMap.asJava))))
      .map(p => (p._1, p._2.flatMap(k => K.fromAVMap(K.key(key.t), k.asScala.toMap).map(UniqueKey(_)))))
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]] =
    QueryResultStream.stream[V](ScanamoQueryRequest(tableName, None, query, queryOptions)).map(_._1)
  def queryFrom[K](query: Query[_], key: UniqueKey[K])(
    implicit K: UniqueKeyCondition[K]
  ): ScanamoOps[(List[Either[DynamoReadError, V]], Option[UniqueKey[K]])] =
    QueryResultStream
      .stream[V](
        ScanamoQueryRequest(tableName, None, query, queryOptions.copy(exclusiveStartKey = Some(key.asAVMap.asJava)))
      )
      .map(p => (p._1, p._2.flatMap(k => K.fromAVMap(K.key(key.t), k.asScala.toMap).map(UniqueKey(_)))))
}
