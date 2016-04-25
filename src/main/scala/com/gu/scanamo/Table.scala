package com.gu.scanamo

import cats.data.Xor
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query.Query

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
}

case class Index[V: DynamoFormat](tableName: String, indexName: String)

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
