package com.gu.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, DeleteItemResult}
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
    * Puts a single item into a table and returns the previous stored item if overwritten by the new one.
    *
    * In case there wasn't any previously stored item, [None] will be returned.
    * If the stored item has a different schema than type [T] then an appropriate [DynamoReadError] will be returned.
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t => 
    * ...   Scanamo.put(client)(t)(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
    * ...   Scanamo.get[Farmer](client)(t)('name -> "McDonald")
    * ... }
    * Some(Right(Farmer(McDonald,156,Farm(List(sheep, cow)))))
    * }}}
    */
  def put[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(item: T): Option[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.put(tableName)(item))

  /**
    * Gets a single item from a table by a unique key
    *
    * {{{
    * >>> case class Rabbit(name: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.putAll(client)(t)((
    * ...   for { _ <- 0 until 100 } yield Rabbit(util.Random.nextString(500))).toSet)
    * ...   Scanamo.scan[Rabbit](client)(t).size
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
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.put(client)(t)(Farmer("Maggot", 75L, Farm(List("dog"))))
    * ...   Scanamo.get[Farmer](client)(t)(UniqueKey(KeyEquals('name, "Maggot")))
    * ... }
    * Some(Right(Farmer(Maggot,75,Farm(List(dog)))))
    * }}}
    * or with some added syntactic sugar:
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.put(client)(t)(Farmer("Maggot", 75L, Farm(List("dog"))))
    * ...   Scanamo.get[Farmer](client)(t)('name -> "Maggot")
    * ... }
    * Some(Right(Farmer(Maggot,75,Farm(List(dog)))))
    * }}}
    * Can also be used with tables that have both a hash and a range key:
    * {{{
    * >>> case class Engine(name: String, number: Int)
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S, 'number -> N) { t =>
    * ...   Scanamo.put(client)(t)(Engine("Thomas", 1))
    * ...   Scanamo.get[Engine](client)(t)('name -> "Thomas" and 'number -> 1)
    * ... }
    * Some(Right(Engine(Thomas,1)))
    * }}}
    */
  def get[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(
      key: UniqueKey[_]): Option[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  /**
    * {{{
    * >>> case class City(name: String, country: String)
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...  Scanamo.put(client)(t)(City("Nashville", "US"))
    * ...  import com.gu.scanamo.syntax._
    * ...  Scanamo.getWithConsistency[City](client)(t)('name -> "Nashville")
    * ... }
    * Some(Right(City(Nashville,US)))
    * }}}
    */
  def getWithConsistency[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(
      key: UniqueKey[_]): Option[Either[DynamoReadError, T]] =
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
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
    * ...   ))
    * ...   Scanamo.getAll[Farmer](client)(t)(UniqueKeys(KeyList('name, Set("Boggis", "Bean"))))
    * ... }
    * Set(Right(Farmer(Bean,55,Farm(List(turkey)))), Right(Farmer(Boggis,43,Farm(List(chicken)))))
    * }}}
    * or with some added syntactic sugar:
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
    * ...   ))
    * ...   Scanamo.getAll[Farmer](client)(t)('name -> Set("Boggis", "Bean"))
    * ... }
    * Set(Right(Farmer(Bean,55,Farm(List(turkey)))), Right(Farmer(Boggis,43,Farm(List(chicken)))))
    * }}}
    * You can also retrieve items from a table with both a hash and range key
    * {{{
    * >>> case class Doctor(actor: String, regeneration: Int)
    * >>> LocalDynamoDB.withRandomTable(client)('actor -> S, 'regeneration -> N) { t =>
    * ...   Scanamo.putAll(client)(t)(
    * ...     Set(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))
    * ...   Scanamo.getAll[Doctor](client)(t)(('actor and 'regeneration) -> Set("McCoy" -> 9, "Ecclestone" -> 11))
    * ... }
    * Set(Right(Doctor(McCoy,9)), Right(Doctor(Ecclestone,11)))
    * }}}
    */
  def getAll[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(
      keys: UniqueKeys[_]): Set[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.getAll(tableName)(keys))

  /**
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> import com.gu.scanamo.query._
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
    * ...   ))
    * ...   Scanamo.getAllWithConsistency[Farmer](client)(t)(UniqueKeys(KeyList('name, Set("Boggis", "Bean"))))
    * ... }
    * Set(Right(Farmer(Bean,55,Farm(List(turkey)))), Right(Farmer(Boggis,43,Farm(List(chicken)))))
    * }}}
    */
  def getAllWithConsistency[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(
      keys: UniqueKeys[_]): Set[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.getAllWithConsistency(tableName)(keys))

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
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.put(client)(t)(Farmer("McGregor", 62L, Farm(List("rabbit"))))
    * ...   Scanamo.delete(client)(t)('name -> "McGregor")
    * ...   Scanamo.get[Farmer](client)(t)('name -> "McGregor")
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
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.putAll(client)(t)(dataSet)
    * ...   Scanamo.deleteAll(client)(t)('name -> dataSet.map(_.name))
    * ...   Scanamo.scan[Farmer](client)(t).toList
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
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('location -> S) { t =>
    * ...   import com.gu.scanamo.syntax._
    * ...   Scanamo.put(client)(t)(Forecast("London", "Rain"))
    * ...   Scanamo.update(client)(t)('location -> "London", set('weather -> "Sun"))
    * ...   Scanamo.scan[Forecast](client)(t).toList
    * ... }
    * List(Right(Forecast(London,Sun)))
    * }}}
    */
  def update[V: DynamoFormat](client: AmazonDynamoDB)(
      tableName: String)(key: UniqueKey[_], expression: UpdateExpression): Either[DynamoReadError, V] =
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
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.put(client)(t)(Bear("Pooh", "honey"))
    * ...   Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets"))
    * ...   Scanamo.scan[Bear](client)(t)
    * ... }
    * List(Right(Bear(Pooh,honey)), Right(Bear(Yogi,picnic baskets)))
    * }}}
    *
    * By default, the entire table contents are read, even if they're more than Dynamo's
    * maximum result set size
    * {{{
    * >>> case class Lemming(name: String, stuff: String)
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.putAll(client)(t)(
    * ...     (for { _ <- 0 until 100 } yield Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toSet
    * ...   )
    * ...   Scanamo.scan[Lemming](client)(t).size
    * ... }
    * 100
    * }}}
    */
  def scan[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String): List[Either[DynamoReadError, T]] =
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
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.put(client)(t)(Bear("Pooh", "honey"))
    * ...   Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets"))
    * ...   Scanamo.scanWithLimit[Bear](client)(t, 1)
    * ... }
    * List(Right(Bear(Pooh,honey)))
    * }}}
    */
  def scanWithLimit[T: DynamoFormat](
      client: AmazonDynamoDB)(tableName: String, limit: Int): List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.scanWithLimit(tableName, limit))

  /**
    * Scan a table, but limiting the number of rows evaluated by Dynamo to `limit`
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTable(client)('name -> S) { t =>
    * ...   Scanamo.put(client)(t)(Bear("Pooh", "honey"))
    * ...   Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets"))
    * ...   val res1 = Scanamo.scanFrom[Bear](client)(t, 1, None)
    * ...   Scanamo.scanFrom[Bear](client)(t, 1, res1._2)._1
    * ... }
    * List(Right(Bear(Yogi,picnic baskets)))
    * }}}
    */
  def scanFrom[T: DynamoFormat](client: AmazonDynamoDB)(
      tableName: String,
      limit: Int,
      startKey: Option[EvaluationKey]): (List[Either[DynamoReadError, T]], Option[EvaluationKey]) =
    exec(client)(ScanamoFree.scanFrom(tableName, limit, startKey))

  /**
    * Returns all items present in the index
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String, alias: Option[String])
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('name -> S)('alias -> S) { (t, i) =>
    * ...   Scanamo.put(client)(t)(Bear("Pooh", "honey", Some("Winnie")))
    * ...   Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets", None))
    * ...   Scanamo.scanIndex[Bear](client)(t, i)
    * ... }
    * List(Right(Bear(Pooh,honey,Some(Winnie))))
    * }}}
    */
  def scanIndex[T: DynamoFormat](
      client: AmazonDynamoDB)(tableName: String, indexName: String): List[Either[DynamoReadError, T]] =
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
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('name -> S)('alias -> S) { (t, i) =>
    * ...   Scanamo.put(client)(t)(Bear("Pooh", "honey", Some("Winnie")))
    * ...   Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets", None))
    * ...   Scanamo.put(client)(t)(Bear("Graham", "quinoa", Some("Guardianista")))
    * ...   Scanamo.scanIndexWithLimit[Bear](client)(t, i, 1)
    * ... }
    * List(Right(Bear(Graham,quinoa,Some(Guardianista))))
    * }}}
    */
  def scanIndexWithLimit[T: DynamoFormat](
      client: AmazonDynamoDB)(tableName: String, indexName: String, limit: Int): List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.scanIndexWithLimit(tableName, indexName, limit))

  /**
    * Scans items present in the index, limiting the number of rows evaluated by Dynamo to `limit`
    * and start the query from the key set as `startKey`
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String, alias: Option[String])
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('name -> S)('alias -> S) { (t, i) =>
    * ...   Scanamo.put(client)(t)(Bear("Pooh", "honey", Some("Winnie")))
    * ...   Scanamo.put(client)(t)(Bear("Yogi", "picnic baskets", None))
    * ...   Scanamo.put(client)(t)(Bear("Graham", "quinoa", Some("Guardianista")))
    * ...   val res1 = Scanamo.scanIndexFrom[Bear](client)(t, i, 1, None)
    * ...   Scanamo.scanIndexFrom[Bear](client)(t, i, 1, res1._2)._1
    * ... }
    * List(Right(Bear(Pooh,honey,Some(Winnie))))
    * }}}
    */
  def scanIndexFrom[T: DynamoFormat](client: AmazonDynamoDB)(
      tableName: String,
      indexName: String,
      limit: Int,
      startKey: Option[EvaluationKey]): (List[Either[DynamoReadError, T]], Option[EvaluationKey]) =
    exec(client)(ScanamoFree.scanIndexFrom(tableName, indexName, limit, startKey))

  /**
    * Perform a query against a table
    *
    * This can be as simple as looking up by a hash key where a range key also exists
    * {{{
    * >>> case class Animal(species: String, number: Int)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> LocalDynamoDB.withRandomTable(client)('species -> S, 'number -> N) { t =>
    * ...   Scanamo.put(client)(t)(Animal("Wolf", 1))
    * ...   import com.gu.scanamo.query._
    * ...   for { i <- 1 to 3 } Scanamo.put(client)(t)(Animal("Pig", i))
    * ...   Scanamo.query[Animal](client)(t)(Query(KeyEquals('species, "Pig")))
    * ... }
    * List(Right(Animal(Pig,1)), Right(Animal(Pig,2)), Right(Animal(Pig,3)))
    * }}}
    * or with some syntactic sugar
    * {{{
    * >>> LocalDynamoDB.withRandomTable(client)('species -> S, 'number -> N) { t =>
    * ...   Scanamo.put(client)(t)(Animal("Wolf", 1))
    * ...   import com.gu.scanamo.syntax._
    * ...   for { i <- 1 to 3 } Scanamo.put(client)(t)(Animal("Pig", i))
    * ...   Scanamo.query[Animal](client)(t)('species -> "Pig")
    * ... }
    * List(Right(Animal(Pig,1)), Right(Animal(Pig,2)), Right(Animal(Pig,3)))
    * }}}
    * It also supports various conditions on the range key
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> case class Transport(mode: String, line: String)
    * >>> LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Transport("Underground", "Circle"),
    * ...     Transport("Underground", "Metropolitan"),
    * ...     Transport("Underground", "Central")))
    * ...   Scanamo.query[Transport](client)(t)('mode -> "Underground" and ('line beginsWith "C"))
    * ... }
    * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
    * }}}
    */
  def query[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(
      query: Query[_]): List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.query(tableName)(query))

  /**
    * Perform a query against a table returning up to `limit` items
    *
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> case class Transport(mode: String, line: String)
    * >>> LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Transport("Underground", "Circle"),
    * ...     Transport("Underground", "Metropolitan"),
    * ...     Transport("Underground", "Central")))
    * ...   Scanamo.queryWithLimit[Transport](client)(t)('mode -> "Underground" and ('line beginsWith "C"), 1)
    * ... }
    * List(Right(Transport(Underground,Central)))
    * }}}
    */
  def queryWithLimit[T: DynamoFormat](client: AmazonDynamoDB)(
      tableName: String)(query: Query[_], limit: Int): List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.queryWithLimit(tableName)(query, limit))

  /**
    * Perform a query against a table returning up to `limit` items starting
    * from `startKey`
    *
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> case class Transport(mode: String, line: String)
    * >>> LocalDynamoDB.withRandomTable(client)('mode -> S, 'line -> S) { t =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Transport("Underground", "Circle"),
    * ...     Transport("Underground", "Metropolitan"),
    * ...     Transport("Underground", "Central")))
    * ...   val res1 = Scanamo.queryFrom[Transport](client)(t)(
    * ...       ('mode -> "Underground" and ('line beginsWith "C")), 1, None)
    * ...   Scanamo.queryFrom[Transport](client)(t)(
    * ...       ('mode -> "Underground" and ('line beginsWith "C")), 1, res1._2)._1
    * ... }
    * List(Right(Transport(Underground,Circle)))
    * }}}
    */
  def queryFrom[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(
      query: Query[_],
      limit: Int,
      startKey: Option[EvaluationKey]): (List[Either[DynamoReadError, T]], Option[EvaluationKey]) =
    exec(client)(ScanamoFree.queryFrom(tableName)(query, limit, startKey))

  /**
    * Query a table using a secondary index
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('mode -> S, 'line -> S)('colour -> S) { (t, i) =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Transport("Underground", "Circle", "Yellow"),
    * ...     Transport("Underground", "Metropolitan", "Magenta"),
    * ...     Transport("Underground", "Central", "Red")))
    * ...   Scanamo.queryIndex[Transport](client)(t, i)('colour -> "Magenta")
    * ... }
    * List(Right(Transport(Underground,Metropolitan,Magenta)))
    * }}}
    */
  def queryIndex[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String, indexName: String)(
      query: Query[_]): List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.queryIndex(tableName, indexName)(query))

  /**
    * Query a table using a secondary index
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)(
    * ...   'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    * ... ) { (t, i) =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Transport("Underground", "Circle", "Yellow"),
    * ...     Transport("Underground", "Metropolitan", "Magenta"),
    * ...     Transport("Underground", "Central", "Red"),
    * ...     Transport("Underground", "Picadilly", "Blue"),
    * ...     Transport("Underground", "Northern", "Black")))
    * ...   Scanamo.queryIndexWithLimit[Transport](client)(t, i)(
    * ...     ('mode -> "Underground" and ('colour beginsWith "Bl")), 1)
    * ... }
    * List(Right(Transport(Underground,Northern,Black)))
    * }}}
    */
  def queryIndexWithLimit[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String, indexName: String)(
      query: Query[_],
      limit: Int): List[Either[DynamoReadError, T]] =
    exec(client)(ScanamoFree.queryIndexWithLimit(tableName, indexName)(query, limit))

  /**
    * Query a table using a secondary index
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)(
    * ...   'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    * ... ) { (t, i) =>
    * ...   Scanamo.putAll(client)(t)(Set(
    * ...     Transport("Underground", "Circle", "Yellow"),
    * ...     Transport("Underground", "Metropolitan", "Magenta"),
    * ...     Transport("Underground", "Central", "Red"),
    * ...     Transport("Underground", "Picadilly", "Blue"),
    * ...     Transport("Underground", "Northern", "Black")))
    * ...   val res1 = Scanamo.queryIndexFrom[Transport](client)(t, i)(
    * ...       ('mode -> "Underground" and ('colour beginsWith "Bl")), 1, None)
    * ...   Scanamo.queryIndexFrom[Transport](client)(t, i)(
    * ...       ('mode -> "Underground" and ('colour beginsWith "Bl")), 1, res1._2)._1
    * ... }
    * List(Right(Transport(Underground,Picadilly,Blue)))
    * }}}
    */
  def queryIndexFrom[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String, indexName: String)(
      query: Query[_],
      limit: Int,
      startKey: Option[EvaluationKey]): (List[Either[DynamoReadError, T]], Option[EvaluationKey]) =
    exec(client)(ScanamoFree.queryIndexFrom(tableName, indexName)(query, limit, startKey))
}
