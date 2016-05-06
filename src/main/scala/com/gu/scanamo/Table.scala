package com.gu.scanamo

import cats.data.Xor
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query._

/**
  * Represents a DynamoDB table that operations can be performed against
  *
  * {{{
  * >>> case class Transport(mode: String, line: String)
  * >>> val transport = Table[Transport]("transport")
  *
  * >>> val client = LocalDynamoDB.client()
  * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
  *
  * >>> LocalDynamoDB.withTable(client)("transport")('mode -> S, 'line -> S) {
  * ...   import com.gu.scanamo.syntax._
  * ...   val operations = for {
  * ...     _ <- transport.putAll(List(
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
  /**
    * A secondary index on the table which can be scanned, or queried against
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    * >>> val transport = Table[Transport]("transport")
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")('mode -> S, 'line -> S)('colour -> S) {
    * ...   val operations = for {
    * ...     _ <- transport.putAll(List(
    * ...       Transport("Underground", "Circle", "Yellow"),
    * ...       Transport("Underground", "Metropolitan", "Maroon"),
    * ...       Transport("Underground", "Central", "Red")))
    * ...     maroonLine <- transport.index("colour-index").query('colour -> "Maroon")
    * ...   } yield maroonLine.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Metropolitan,Maroon)))
    * }}}
    */
  def index(indexName: String) = Index[V](name, indexName)

  def put(v: V) = ScanamoFree.put(name)(v)
  def putAll(vs: List[V]) = ScanamoFree.putAll(name)(vs)
  def get(key: UniqueKey[_]) = ScanamoFree.get[V](name)(key)
  def getAll(keys: UniqueKeys[_]) = ScanamoFree.getAll[V](name)(keys)
  def delete(key: UniqueKey[_]) = ScanamoFree.delete(name)(key)

  /**
    * Performs the chained operation if the condition is met
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.gu.scanamo.query._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> val farmersTable = Table[Farmer]("nursery-farmers")
    * >>> LocalDynamoDB.withTable(client)("nursery-farmers")('name -> S) {
    * ...   val farmerOps = for {
    * ...     _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
    * ...     _ <- farmersTable.given('age -> 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"))))
    * ...     _ <- farmersTable.given('age -> 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"))))
    * ...     farmerWithNewStock <- farmersTable.get('name -> "McDonald")
    * ...   } yield farmerWithNewStock
    * ...   Scanamo.exec(client)(farmerOps)
    * ... }
    * Some(Right(Farmer(McDonald,156,Farm(List(sheep, chicken)))))
    *
    * >>> case class Thing(a: String, maybe: Option[Int])
    * >>> val thingTable = Table[Thing]("things")
    * >>> LocalDynamoDB.withTable(client)("things")('a -> S) {
    * ...   val ops = for {
    * ...     _ <- thingTable.put(Thing("a", None))
    * ...     _ <- thingTable.put(Thing("b", Some(1)))
    * ...     _ <- thingTable.given(attributeExists('maybe)).put(Thing("a", Some(2)))
    * ...     _ <- thingTable.given(attributeExists('maybe)).put(Thing("b", Some(3)))
    * ...     things <- thingTable.scan()
    * ...   } yield things
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Thing(b,Some(3))), Right(Thing(a,None)))
    * }}}
    */
  def given[T: PutConditionState](condition: ConditionExpression[T]) = ScanamoFree.given(name)(condition)
}

private[scanamo] case class Index[V: DynamoFormat](tableName: String, indexName: String)

/* typeclass */trait Scannable[T[_], V] {
  def scan(t: T[V])(): ScanamoOps[Stream[Xor[DynamoReadError, V]]]
}

object Scannable {
  def apply[T[_], V](implicit s: Scannable[T, V]) = s

  trait Ops[T[_], V] {
    val instance: Scannable[T, V]
    def self: T[V]
    def scan() = instance.scan(self)()
  }

  trait ToScannableOps {
    implicit def scannableOps[T[_], V](t: T[V])(implicit s: Scannable[T, V]) = new Ops[T, V] {
      val instance = s
      val self = t
    }
  }

  implicit def tableScannable[V: DynamoFormat] = new Scannable[Table, V] {
    override def scan(t: Table[V])(): ScanamoOps[Stream[Xor[DynamoReadError, V]]] =
      ScanamoFree.scan[V](t.name)
  }
  implicit def indexScannable[V: DynamoFormat] = new Scannable[Index, V] {
    override def scan(i: Index[V])(): ScanamoOps[Stream[Xor[DynamoReadError, V]]] =
      ScanamoFree.scanIndex[V](i.tableName, i.indexName)
  }
}

/* typeclass */ trait Queryable[T[_], V] {
  def query(t: T[V])(query: Query[_]): ScanamoOps[Stream[Xor[DynamoReadError, V]]]
}

object Queryable {
  def apply[T[_], V](implicit s: Queryable[T, V]) = s

  trait Ops[T[_], V] {
    val instance: Queryable[T, V]
    def self: T[V]
    def query(query: Query[_]) = instance.query(self)(query)
  }

  trait ToQueryableOps {
    implicit def queryableOps[T[_], V](t: T[V])(implicit s: Queryable[T, V]) = new Ops[T, V] {
      val instance = s
      val self = t
    }
  }

  implicit def tableQueryable[V: DynamoFormat] = new Queryable[Table, V] {
    override def query(t: Table[V])(query: Query[_]): ScanamoOps[Stream[Xor[DynamoReadError, V]]] =
      ScanamoFree.query[V](t.name)(query)
  }
  implicit def indexQueryable[V: DynamoFormat] = new Queryable[Index, V] {
    override def query(i: Index[V])(query: Query[_]): ScanamoOps[Stream[Xor[DynamoReadError, V]]] =
      ScanamoFree.queryIndex[V](i.tableName, i.indexName)(query)
  }
}
