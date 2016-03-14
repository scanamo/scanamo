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
  * >>> import com.amazonaws.services.dynamodbv2.model._
  * >>> val client = LocalDynamoDB.client()
  * >>> val createFarmersTableResult = LocalDynamoDB.createTable(client, "farmers", List("name" -> ScalarAttributeType.S), List("name" -> KeyType.HASH))
  * >>> val createFarmHandsTableResult = LocalDynamoDB.createTable(client, "farmhands", List("boss" -> ScalarAttributeType.S, "employeeId" -> ScalarAttributeType.N), List("boss" -> KeyType.HASH, "employeeId" -> KeyType.RANGE))
  * }}}
  */
object Scanamo {
  import ScanamoRequest._

  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
    * >>> Scanamo.get[String, Farmer](client)("farmers")('name -> "McDonald")
    * Some(Valid(Farmer(McDonald,156,Farm(List(sheep, cow)))))
    * }}}
    */
  def put[T](client: AmazonDynamoDB)(tableName: String)(item: T)(implicit f: DynamoFormat[T]): PutItemResult =
    client.putItem(putRequest(tableName)(item))

  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("Maggot", 75L, Farm(List("dog"))))
    * >>> Scanamo.get[String, Farmer](client)("farmers")('name -> "Maggot")
    * Some(Valid(Farmer(Maggot,75,Farm(List(dog)))))
    * }}}
    */
  def get[HK, T](client: AmazonDynamoDB)(tableName: String)(hashkey: (Symbol, HK))
    (implicit fhk: DynamoFormat[HK], ft: DynamoFormat[T]): Option[ValidatedNel[DynamoReadError, T]] =
    Option(client.getItem(getRequest[HK](tableName)(hashkey)).getItem).map(read[T])

  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    * >>> case class FarmHand(boss: String, employeeId: Int, age: Long)
    *
    * >>> val putFarmerResult = Scanamo.put(client)("farmers")(Farmer("Maniappa", 75L, Farm(List("cow"))))
    * >>> val putFarmHandResult = Scanamo.put(client)("farmhands")(FarmHand("Maniappa", 1, 27L))
    * >>> Scanamo.get[String, Int, FarmHand](client)("farmhands")('boss -> "Maniappa", 'employeeId -> 1)
    * Some(Valid(FarmHand(Maniappa,1,27)))
    * }}}
    */
  def get[HK, RK, T](client: AmazonDynamoDB)(tableName: String)(hashkey: (Symbol, HK), rangekey: (Symbol, RK))
    (implicit fhk: DynamoFormat[HK], frk: DynamoFormat[RK], ft: DynamoFormat[T]): Option[ValidatedNel[DynamoReadError, T]] =
    Option(client.getItem(getRequest[HK, RK](tableName)(hashkey, rangekey)).getItem).map(read[T])

  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> val putResult = Scanamo.put(client)("farmers")(Farmer("McGregor", 62L, Farm(List("rabbit"))))
    * >>> val deleteResult = Scanamo.delete[String, Farmer](client)("farmers")('name -> "McGregor")
    * >>> Scanamo.get[String, Farmer](client)("farmers")('name -> "McGregor")
    * None
    * }}}
    */
  def delete[HK, T](client: AmazonDynamoDB)(tableName: String)(hashkey: (Symbol, HK))
    (implicit fhk: DynamoFormat[HK], ft: DynamoFormat[T]): DeleteItemResult =
    client.deleteItem(deleteRequest[HK](tableName)(hashkey))

  /**
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    * >>> case class FarmHand(boss: String, employeeId: Int, age: Long)
    *
    * >>> val farmerPutResult = Scanamo.put(client)("farmers")(Farmer("Balthazar", 62L, Farm(List("rabbit"))))
    * >>> val farmHandPutResult = Scanamo.put(client)("farmhands")(FarmHand("Balthazar", 123, 25L))
    * >>> val deleteResult = Scanamo.delete[String, Int, FarmHand](client)("farmhands")('boss -> "Balthazar", 'employeeId -> 123)
    * >>> Scanamo.get[String, Int, FarmHand](client)("farmhands")('boss -> "Balthazar", 'employeeId -> 123)
    * None
    * }}}
    */
  def delete[HK, RK, T](client: AmazonDynamoDB)(tableName: String)(hashkey: (Symbol, HK), rangekey: (Symbol, RK))
    (implicit fhk: DynamoFormat[HK], frk: DynamoFormat[RK], ft: DynamoFormat[T]): DeleteItemResult =
    client.deleteItem(deleteRequest[HK, RK](tableName)(hashkey, rangekey))

  /**
    * Lazily scans a DynamoDB table
    *
    * Does not cache results by default
    * {{{
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model._
    * >>> val createTableResult = LocalDynamoDB.createTable(client, "bears", List("name" -> ScalarAttributeType.S), List("name" -> KeyType.HASH))
    *
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val r1 = Scanamo.put(client)("bears")(Bear("Pooh", "honey"))
    * >>> val r2 = Scanamo.put(client)("bears")(Bear("Yogi", "picnic baskets"))
    * >>> Scanamo.scan[Bear](client)("bears").toList
    * List(Valid(Bear(Pooh,honey)), Valid(Bear(Yogi,picnic baskets)))
    *
    * >>> val lemmingTableResult = LocalDynamoDB.createTable(client, "lemmings", List("name" -> ScalarAttributeType.S), List("name" -> KeyType.HASH))
    * >>> case class Lemming(name: String, stuff: String)
    * >>> val lemmingResults = for { _ <- 0 until 100 } yield Scanamo.put(client)("lemmings")(Lemming(util.Random.nextString(500), util.Random.nextString(5000)))
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
    * {{{
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val r1 = Scanamo.put(client)("bears")(Bear("Pooh", "honey"))
    * >>> val r2 = Scanamo.put(client)("bears")(Bear("Yogi", "picnic baskets"))
    * >>> Scanamo.query[Bear, String](client)("bears")('name -> "Pooh").toList
    * List(Valid(Bear(Pooh,honey)))
    * }}}
    */
  def query[T, K](client: AmazonDynamoDB)(tableName: String)(key: (Symbol, K))(
    implicit f: DynamoFormat[T], kf: DynamoFormat[K]): Streaming[ValidatedNel[DynamoReadError, T]] = {

    QueryResultStream.stream[T](client)(
      queryRequest(tableName)(key)
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
