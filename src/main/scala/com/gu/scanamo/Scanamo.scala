package com.gu.scanamo

import cats.data.{Streaming, ValidatedNel}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}

/**
  * Scanamo provides a simplified interface for reading and writing case classes to DynamoDB
  *
  * The examples in method documentation assume the following table has been created:
  * {{{
  * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
  * >>> val client = LocalDynamoDB.client()
  * >>> val createTableResult = LocalDynamoDB.createTable(client)("farmers")('name -> S)
  * }}}
  */
object Scanamo {
  import ScanamoRequest._

  def exec[A](client: AmazonDynamoDB)(op: ScanamoOps[A]) = op.foldMap(ScanamoInterpreters.id(client))

  /**
    * Puts a single item into a table
    *
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
    * >>> import com.gu.scanamo.syntax._
    * >>> Scanamo.get[Farmer](client)("farmers")('name -> "McDonald")
    * Some(Valid(Farmer(McDonald,156,Farm(List(sheep, cow)))))
    * }}}
    */
  def put[T](client: AmazonDynamoDB)(tableName: String)(item: T)(implicit f: DynamoFormat[T]): PutItemResult =
    exec(client)(ScanamoFree.put(tableName)(item))

  /**
    * Gets a single item from a table by a unique key
    *
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> case class Rabbit(name: String)
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val createTableResult = LocalDynamoDB.createTable(client)("rabbits")('name -> S)
    * >>> val multiPut = Scanamo.putAll(client)("rabbits")((
    * ...   for { _ <- 0 until 100 } yield Rabbit(util.Random.nextString(500))).toList)
    * >>> Scanamo.scan[Rabbit](client)("rabbits").toList.size
    * 100
    * }}}
    */
  def putAll[T](client: AmazonDynamoDB)(tableName: String)(items: List[T])(implicit f: DynamoFormat[T]): List[BatchWriteItemResult] =
    (for {
      batch <- items.grouped(25)
    } yield client.batchWriteItem(batchPutRequest(tableName)(batch))).toList

  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("Maggot", 75L, Farm(List("dog"))))
    * >>> Scanamo.get[Farmer](client)("farmers")(UniqueKey(KeyEquals('name, "Maggot")))
    * Some(Valid(Farmer(Maggot,75,Farm(List(dog)))))
    * }}}
    * or with some added syntactic sugar:
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> Scanamo.get[Farmer](client)("farmers")('name -> "Maggot")
    * Some(Valid(Farmer(Maggot,75,Farm(List(dog)))))
    * }}}
    * Can also be used with tables that have both a hash and a range key:
    * {{{
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val createTableResult = LocalDynamoDB.createTable(client)("engines")('name -> S, 'number -> N)
    * >>> case class Engine(name: String, number: Int)
    * >>> val thomas = Scanamo.put(client)("engines")(Engine("Thomas", 1))
    * >>> Scanamo.get[Engine](client)("engines")('name -> "Thomas" and 'number -> 1)
    * Some(Valid(Engine(Thomas,1)))
    * }}}
    */
  def get[T](client: AmazonDynamoDB)(tableName: String)(key: UniqueKey[_])
    (implicit ft: DynamoFormat[T]): Option[ValidatedNel[DynamoReadError, T]] =
    exec(client)(ScanamoFree.get[T](tableName)(key))

  /**
    * Returns all the items in the table with matching keys
    *
    * Results are returned in the same order as the keys are provided
    *
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> val putResult = Scanamo.putAll(client)("farmers")(List(
    * ...   Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
    * ... ))
    * >>> Scanamo.getAll[Farmer](client)("farmers")(UniqueKeys(KeyList('name, List("Boggis", "Bean"))))
    * List(Valid(Farmer(Boggis,43,Farm(List(chicken)))), Valid(Farmer(Bean,55,Farm(List(turkey)))))
    * }}}
    * or with some added syntactic sugar:
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> Scanamo.getAll[Farmer](client)("farmers")('name -> List("Boggis", "Bean"))
    * List(Valid(Farmer(Boggis,43,Farm(List(chicken)))), Valid(Farmer(Bean,55,Farm(List(turkey)))))
    * }}}
    * You can also retrieve items from a table with both a hash and range key
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> case class Doctor(actor: String, regeneration: Int)
    * >>> val doctorsTableResult = LocalDynamoDB.createTable(client)("doctors")('actor -> S, 'regeneration -> N)
    *
    * >>> val putDoctorResult = Scanamo.putAll(client)("doctors")(
    * ...   List(Doctor("McCoy", 9), Doctor("Ecclestone", 10), Doctor("Ecclestone", 11)))
    * >>> Scanamo.getAll[Doctor](client)("doctors")(('actor and 'regeneration) -> List("McCoy" -> 9, "Ecclestone" -> 11))
    * List(Valid(Doctor(McCoy,9)), Valid(Doctor(Ecclestone,11)))
    * }}}
    */
  def getAll[T: DynamoFormat](client: AmazonDynamoDB)(tableName: String)(keys: UniqueKeys[_]): List[ValidatedNel[DynamoReadError, T]] = {
    import collection.convert.decorateAsScala._
    keys.sortByKeys(client.batchGetItem(batchGetRequest(tableName)(keys)).getResponses.get(tableName).asScala.toList)
      .map(read[T])
  }

  /**
    * Deletes a single item from a table by a unique key
    *
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("McGregor", 62L, Farm(List("rabbit"))))
    * >>> import com.gu.scanamo.syntax._
    * >>> val deleteResult = Scanamo.delete(client)("farmers")('name -> "McGregor")
    * >>> Scanamo.get[Farmer](client)("farmers")('name -> "McGregor")
    * None
    * }}}
    */
  def delete(client: AmazonDynamoDB)(tableName: String)(key: UniqueKey[_]): DeleteItemResult =
    exec(client)(ScanamoFree.delete(tableName)(key))

  /**
    * Lazily scans a table
    *
    * Does not cache results by default
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val createTableResult = LocalDynamoDB.createTable(client)("bears")('name -> S)
    *
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val r1 = Scanamo.put(client)("bears")(Bear("Pooh", "honey"))
    * >>> val r2 = Scanamo.put(client)("bears")(Bear("Yogi", "picnic baskets"))
    * >>> Scanamo.scan[Bear](client)("bears").toList
    * List(Valid(Bear(Pooh,honey)), Valid(Bear(Yogi,picnic baskets)))
    * }}}
    * Pagination is handled internally with `Streaming` result retrieving pages as necessary
    * {{{
    * >>> val lemmingTableResult = LocalDynamoDB.createTable(client)("lemmings")('name -> S)
    * >>> case class Lemming(name: String, stuff: String)
    * >>> val lemmingResults = Scanamo.putAll(client)("lemmings")(
    * ...   (for { _ <- 0 until 100 } yield Lemming(util.Random.nextString(500), util.Random.nextString(5000))).toList
    * ... )
    * >>> Scanamo.scan[Lemming](client)("lemmings").toList.size
    * 100
    * }}}
    */
  def scan[T](client: AmazonDynamoDB)(tableName: String)(implicit f: DynamoFormat[T]): Streaming[ValidatedNel[DynamoReadError, T]] = {
    ScanResultStream.stream[T](client)(
      new ScanRequest().withTableName(tableName)
    )
  }

  /**
    * Perform a query against a table
    *
    * This can be as simple as looking up by a hash key where a range key also exists
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val createTableResult = LocalDynamoDB.createTable(client)("animals")('species -> S, 'number -> N)
    * >>> case class Animal(species: String, number: Int)
    *
    * >>> val r1 = Scanamo.put(client)("animals")(Animal("Wolf", 1))
    * >>> val r2 = for { i <- 1 to 3 } Scanamo.put(client)("animals")(Animal("Pig", i))
    * * >>> Scanamo.query[Animal](client)("animals")(Query(KeyEquals('species, "Pig"))).toList
    * List(Valid(Animal(Pig,1)), Valid(Animal(Pig,2)), Valid(Animal(Pig,3)))
    * }}}
    * or with some syntactic sugar
    * {{{
    * >>> import com.gu.scanamo.syntax._
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig").toList
    * List(Valid(Animal(Pig,1)), Valid(Animal(Pig,2)), Valid(Animal(Pig,3)))
    * }}}
    * It also supports various conditions on the range key
    * {{{
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig" and 'number < 3).toList
    * List(Valid(Animal(Pig,1)), Valid(Animal(Pig,2)))
    *
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig" and 'number > 1).toList
    * List(Valid(Animal(Pig,2)), Valid(Animal(Pig,3)))
    *
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig" and 'number <= 2).toList
    * List(Valid(Animal(Pig,1)), Valid(Animal(Pig,2)))
    *
    * >>> Scanamo.query[Animal](client)("animals")('species -> "Pig" and 'number >= 2).toList
    * List(Valid(Animal(Pig,2)), Valid(Animal(Pig,3)))
    *
    * >>> val transportTableResult = LocalDynamoDB.createTable(client)("transport")('mode -> S, 'line -> S)
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val lines = Scanamo.putAll(client)("transport")(List(
    * ...   Transport("Underground", "Circle"),
    * ...   Transport("Underground", "Metropolitan"),
    * ...   Transport("Underground", "Central")))
    *
    * >>> Scanamo.query[Transport](client)("transport")('mode -> "Underground" and ('line beginsWith "C")).toList
    * List(Valid(Transport(Underground,Central)), Valid(Transport(Underground,Circle)))
    * }}}
    */
  def query[T](client: AmazonDynamoDB)(tableName: String)(keyCondition: Query[_])(
    implicit f: DynamoFormat[T]
  ) : Streaming[ValidatedNel[DynamoReadError, T]] = {

    QueryResultStream.stream[T](client)(
      queryRequest(tableName)(keyCondition)
    )
  }

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    *
    * prop> (m: Map[String, Int]) =>
    *     |   Scanamo.read[Map[String, Int]](
    *     |     m.mapValues(i => new AttributeValue().withN(i.toString)).asJava
    *     |   ) == cats.data.Validated.valid(m)
    * }}}
    */
  def read[T](m: java.util.Map[String, AttributeValue])(implicit f: DynamoFormat[T]): ValidatedNel[DynamoReadError, T] =
    f.read(new AttributeValue().withM(m))
}
