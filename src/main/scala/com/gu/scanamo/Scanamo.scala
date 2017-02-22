package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, DeleteItemResult, PutItemResult, QueryRequest}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{ScanamoInterpreters, ScanamoOps}
import com.gu.scanamo.query._
import com.gu.scanamo.update.UpdateExpression

/**
  * Provides a simplified interface for reading and writing case classes to DynamoDB
  *
  * To avoid blocking, use [[com.gu.scanamo.ScanamoAsync]]
  */
object Scanamo {

  def exec[A](client: AmazonDynamoDB)(op: ScanamoOps[A]): A = op.foldMap(ScanamoInterpreters.id(client))

  /**
    * Puts a single item into a table
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> LocalDynamoDB.withTable(client)("farmers")('name -> S) {
    * ...   Scanamo.put(client)("farmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
    * ...   Scanamo.get[Farmer](client)("farmers")('name -> "McDonald")
    * ... }
    * Some(Right(Farmer(McDonald,156,Farm(List(sheep, cow)))))
    * }}}
    */
  def put[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(item: T): PutItemResult =
    exec(client)(ScanamoFree.put(tableName)(item))

  /**
    * Gets a single item from a table by a unique key
    *
    * {{{
    * >>> case class Rabbit(name: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> LocalDynamoDB.withTable(client)("rabbits")('name -> S) {
    * ...   Scanamo.putAll(client)("rabbits")((
    * ...   for { _ <- 0 until 100 } yield Rabbit(util.Random.nextString(500))).toSet)
    * ...   Scanamo.scan[Rabbit](client)("rabbits").size
    * ... }
    * 100
    * }}}
    */
  def putAll[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(items: Set[T]): List[BatchWriteItemResult] =
    exec(client)(ScanamoFree.putAll(tableName)(items))

  /**
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> import com.gu.scanamo.query._
    * >>> LocalDynamoDB.withTable(client)("farmers")('name -> S) {
    * ...   Scanamo.put(client)("farmers")(Farmer("Maggot", 75L, Farm(List("dog"))))
    * ...   Scanamo.get[Farmer](client)("farmers")(UniqueKey(KeyEquals('name, "Maggot")))
    * ... }
    * Some(Right(Farmer(Maggot,75,Farm(List(dog)))))
    * }}}
    * or with some added syntactic sugar:
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> LocalDynamoDB.withTable(client)("farmers")('name -> S) {
    * ...   Scanamo.put(client)("farmers")(Farmer("Maggot", 75L, Farm(List("dog"))))
    * ...   Scanamo.get[Farmer](client)("farmers")('name -> "Maggot")
    * ... }
    * Some(Right(Farmer(Maggot,75,Farm(List(dog)))))
    * }}}
    * Can also be used with tables that have both a hash and a range key:
    * {{{
    * >>> case class Engine(name: String, number: Int)
    * >>> LocalDynamoDB.withTable(client)("engines")('name -> S, 'number -> N) {
    * ...   Scanamo.put(client)("engines")(Engine("Thomas", 1))
    * ...   Scanamo.get[Engine](client)("engines")('name -> "Thomas" and 'number -> 1)
    * ... }
    * Some(Right(Engine(Thomas,1)))
    * }}}
    */
  def get[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(key: UniqueKey[_])
    : Option[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  /**
    * {{{
    * >>> case class City(name: String, country: String)
    * >>> val cityTable = Table[City]("asyncCities")
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    * >>> LocalDynamoDB.withTable(client)("asyncCities")('name -> S) {
    * ...  Scanamo.put(client)("asyncCities")(City("Nashville", "US"))
    * ...  import com.gu.scanamo.syntax._
    * ...  Scanamo.getWithConsistency[City](client)("asyncCities")('name -> "Nashville")
    * ... }
    * Some(Right(City(Nashville,US)))
    * }}}
    */
  def getWithConsistency[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(key: UniqueKey[_])
    : Option[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.getWithConsistency[T](tableName)(key))

  /**
    * Returns all the items in the table with matching keys
    *
    * Results are returned in the same order as the keys are provided
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> import com.gu.scanamo.query._
    * >>> LocalDynamoDB.withTable(client)("farmers")('name -> S) {
    * ...   Scanamo.putAll(client)("farmers")(Set(
    * ...     Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
    * ...   ))
    * ...   Scanamo.getAll[Farmer](client)("farmers")(UniqueKeys(KeyList('name, Set("Boggis", "Bean"))))
    * ... }
    * Set(Right(Farmer(Bean,55,Farm(List(turkey)))), Right(Farmer(Boggis,43,Farm(List(chicken)))))
    * }}}
    * or with some added syntactic sugar:
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> LocalDynamoDB.withTable(client)("farmers")('name -> S) {
    * ...   Scanamo.putAll(client)("farmers")(Set(
    * ...     Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
    * ...   ))
    * ...   Scanamo.getAll[Farmer](client)("farmers")('name -> Set("Boggis", "Bean"))
    * ... }
    * Set(Right(Farmer(Bean,55,Farm(List(turkey)))), Right(Farmer(Boggis,43,Farm(List(chicken)))))
    * }}}
    * You can also retrieve items from a table with both a hash and range key
    * {{{
    * >>> case class Doctor(actor: String, regeneration: Int)
    * >>> LocalDynamoDB.withTable(client)("doctors")('actor -> S, 'regeneration -> N) {
    * ...   Scanamo.putAll(client)("doctors")(
    * ...     Set(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))
    * ...   Scanamo.getAll[Doctor](client)("doctors")(('actor and 'regeneration) -> Set("McCoy" -> 9, "Ecclestone" -> 11))
    * ... }
    * Set(Right(Doctor(McCoy,9)), Right(Doctor(Ecclestone,11)))
    * }}}
    */
  def getAll[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(keys: UniqueKeys[_])
    : Set[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.getAll(tableName)(keys))


  /**
    * Deletes a single item from a table by a unique key
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> LocalDynamoDB.withTable(client)("farmers")('name -> S) {
    * ...   Scanamo.put(client)("farmers")(Farmer("McGregor", 62L, Farm(List("rabbit"))))
    * ...   Scanamo.delete(client)("farmers")('name -> "McGregor")
    * ...   Scanamo.get[Farmer](client)("farmers")('name -> "McGregor")
    * ... }
    * None
    * }}}
    */
  def delete(client: AmazonDynamoDB)(tableName: String)(key: UniqueKey[_]): DeleteItemResult =
    exec(client)(ScanamoFree.delete(tableName)(key))

  /**
    * Deletes multiple items from a table by a unique key
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
    *
    * >>> LocalDynamoDB.withTable(client)("farmers")('name -> S) {
    * ...   Scanamo.putAll(client)("farmers")(dataSet)
    * ...   Scanamo.deleteAll(client)("farmers")('name -> dataSet.map(_.name))
    * ...   Scanamo.scan[Farmer](client)("farmers").toList
    * ... }
    * List()
    * }}}
    */
  def deleteAll(client: AmazonDynamoDB)(tableName: String)(items: UniqueKeys[_]): List[BatchWriteItemResult] =
    exec(client)(ScanamoFree.deleteAll(tableName)(items))

  /**
    * Updates an attribute that is not part of the key
    *
    * {{{
    * >>> case class Forecast(location: String, weather: String)
    * >>> val forecast = Table[Forecast]("forecast")
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTable(client)("forecast")('location -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   Scanamo.put(client)("forecast")(Forecast("London", "Rain"))
    * ...   Scanamo.update(client)("forecast")('location -> "London", set('weather -> "Sun"))
    * ...   Scanamo.scan[Forecast](client)("forecast").toList
    * ... }
    * List(Right(Forecast(London,Sun)))
    * }}}
    */
  def update[V: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(key: UniqueKey[_], expression: UpdateExpression): Either[DynamoReadError, V] =
    exec(client)(ScanamoFree.update[V](tableName)(key)(expression))

  /**
    * Scans all elements of a table
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTable(client)("bears")('name -> S) {
    * ...   Scanamo.put(client)("bears")(Bear("Pooh", "honey"))
    * ...   Scanamo.put(client)("bears")(Bear("Yogi", "picnic baskets"))
    * ...   Scanamo.scan[Bear](client)("bears")
    * ... }
    * List(Right(Bear(Pooh,honey)), Right(Bear(Yogi,picnic baskets)))
    * }}}
    *
    * By default, the entire table contents are read, even if they're more than Dynamo's
    * maximum result set size
    * {{{
    * >>> case class Lemming(name: String, stuff: String)
    *
    * >>> LocalDynamoDB.withTable(client)("lemmings")('name -> S) {
    * ...   Scanamo.putAll(client)("lemmings")(
    * ...     (for { _ <- 0 until 100 } yield Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toSet
    * ...   )
    * ...   Scanamo.scan[Lemming](client)("lemmings").size
    * ... }
    * 100
    * }}}
    */
  def scan[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)
    : List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.scan(tableName))

  /**
    * Scan a table, but limiting the number of rows evaluated by Dynamo to `limit`
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTable(client)("bears")('name -> S) {
    * ...   Scanamo.put(client)("bears")(Bear("Pooh", "honey"))
    * ...   Scanamo.put(client)("bears")(Bear("Yogi", "picnic baskets"))
    * ...   Scanamo.scanWithLimit[Bear](client)("bears", 1)
    * ... }
    * List(Right(Bear(Pooh,honey)))
    * }}}
    */
  def scanWithLimit[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String, limit: Int)
    : List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit))

  /**
    * Returns all items present in the index
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String, alias: Option[String])
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("bears", "alias-index")('name -> S)('alias -> S) {
    * ...   Scanamo.put(client)("bears")(Bear("Pooh", "honey", Some("Winnie")))
    * ...   Scanamo.put(client)("bears")(Bear("Yogi", "picnic baskets", None))
    * ...   Scanamo.scanIndex[Bear](client)("bears", "alias-index")
    * ... }
    * List(Right(Bear(Pooh,honey,Some(Winnie))))
    * }}}
    */
  def scanIndex[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String, indexName: String)
  : List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.scanIndex(tableName, indexName))

  /**
    * Scans items present in the index, limiting the number of rows evaluated by Dynamo to `limit`
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String, alias: Option[String])
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("bears", "alias-index")('name -> S)('alias -> S) {
    * ...   Scanamo.put(client)("bears")(Bear("Pooh", "honey", Some("Winnie")))
    * ...   Scanamo.put(client)("bears")(Bear("Yogi", "picnic baskets", None))
    * ...   Scanamo.put(client)("bears")(Bear("Graham", "quinoa", Some("Guardianista")))
    * ...   Scanamo.scanIndexWithLimit[Bear](client)("bears", "alias-index", 1)
    * ... }
    * List(Right(Bear(Graham,quinoa,Some(Guardianista))))
    * }}}
    */
  def scanIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String, indexName: String, limit: Int)
  : List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit))

  /**
    * Perform a query against a table
    *
    * This can be as simple as looking up by a hash key where a range key also exists
    * {{{
    * >>> case class Animal(species: String, number: Int)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val tableResult = LocalDynamoDB.createTable(client)("animals")('species -> S, 'number -> N)
    *
    * >>> val r1 = Scanamo.put(client)("animals")(Animal("Wolf", 1))
    * >>> import com.gu.scanamo.query._
    * >>> val r2 = for { i <- 1 to 3 } Scanamo.put(client)("animals")(Animal("Pig", i))
    * >>> Scanamo.query[Animal](client)("animals")(Query(KeyEquals('species, "Pig")))
    * List(Right(Animal(Pig,1)), Right(Animal(Pig,2)), Right(Animal(Pig,3)))
    * }}}
    * or with some syntactic sugar
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig")
    * List(Right(Animal(Pig,1)), Right(Animal(Pig,2)), Right(Animal(Pig,3)))
    * }}}
    * It also supports various conditions on the range key
    * {{{
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig" and 'number < 3)
    * List(Right(Animal(Pig,1)), Right(Animal(Pig,2)))
    *
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig" and 'number > 1)
    * List(Right(Animal(Pig,2)), Right(Animal(Pig,3)))
    *
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig" and 'number <= 2)
    * List(Right(Animal(Pig,1)), Right(Animal(Pig,2)))
    *
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig" and 'number >= 2)
    * List(Right(Animal(Pig,2)), Right(Animal(Pig,3)))
    *
    * >>> case class Transport(mode: String, line: String)
    * >>> LocalDynamoDB.withTable(client)("transport")('mode -> S, 'line -> S) {
    * ...   Scanamo.putAll(client)("transport")(Set(
    * ...     Transport("Underground", "Circle"),
    * ...     Transport("Underground", "Metropolitan"),
    * ...     Transport("Underground", "Central")))
    * ...   Scanamo.query[Transport](client)("transport")('mode -> "Underground" and ('line beginsWith "C"))
    * ... }
    * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
    * }}}
    * To have results returned in descending range key order, append `descending` to your query:
    * {{{
    * >>> Scanamo.query[Animal](client)("animals")(('species -> "Pig").descending)
    * List(Right(Animal(Pig,3)), Right(Animal(Pig,2)), Right(Animal(Pig,1)))
    *
    * >>> Scanamo.query[Animal](client)("animals")(('species -> "Pig" and 'number < 3).descending)
    * List(Right(Animal(Pig,2)), Right(Animal(Pig,1)))
    * }}}
    */
  def query[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(query: Query[_], queryRequest: QueryRequest = new QueryRequest())
    : List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.query(tableName)(query, queryRequest))

  /**
    * Perform a query against a table returning up to `limit` items
    *
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> case class Transport(mode: String, line: String)
    * >>> LocalDynamoDB.withTable(client)("transport")('mode -> S, 'line -> S) {
    * ...   Scanamo.putAll(client)("transport")(Set(
    * ...     Transport("Underground", "Circle"),
    * ...     Transport("Underground", "Metropolitan"),
    * ...     Transport("Underground", "Central")))
    * ...   Scanamo.queryWithLimit[Transport](client)("transport")('mode -> "Underground" and ('line beginsWith "C"), 1)
    * ... }
    * List(Right(Transport(Underground,Central)))
    * }}}
    */
  def queryWithLimit[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(query: Query[_], limit: Int, queryRequest: QueryRequest = new QueryRequest())
  : List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit, queryRequest))

  /**
    * Query a table using a secondary index
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")('mode -> S, 'line -> S)('colour -> S) {
    * ...   Scanamo.putAll(client)("transport")(Set(
    * ...     Transport("Underground", "Circle", "Yellow"),
    * ...     Transport("Underground", "Metropolitan", "Magenta"),
    * ...     Transport("Underground", "Central", "Red")))
    * ...   Scanamo.queryIndex[Transport](client)("transport", "colour-index")('colour -> "Magenta")
    * ... }
    * List(Right(Transport(Underground,Metropolitan,Magenta)))
    * }}}
    */
  def queryIndex[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String, indexName: String)(query: Query[_], queryRequest: QueryRequest = new QueryRequest())
  : List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query, queryRequest))

  /**
    * Query a table using a secondary index
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")(
    * ...   'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    * ... ) {
    * ...   Scanamo.putAll(client)("transport")(Set(
    * ...     Transport("Underground", "Circle", "Yellow"),
    * ...     Transport("Underground", "Metropolitan", "Magenta"),
    * ...     Transport("Underground", "Central", "Red"),
    * ...     Transport("Underground", "Picadilly", "Blue"),
    * ...     Transport("Underground", "Northern", "Black")))
    * ...   Scanamo.queryIndexWithLimit[Transport](client)("transport", "colour-index")(
    * ...     ('mode -> "Underground" and ('colour beginsWith "Bl")), 1)
    * ... }
    * List(Right(Transport(Underground,Northern,Black)))
    * }}}
    */
  def queryIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String, indexName: String)(query: Query[_], limit: Int, queryRequest: QueryRequest = new QueryRequest())
  : List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit, queryRequest))
}
