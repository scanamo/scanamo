package org.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.scanamo.ops.{ScanamoInterpreters, ScanamoOps}

/**
  * Provides a simplified interface for reading and writing case classes to DynamoDB
  *
  * To avoid blocking, use [[org.scanamo.ScanamoAsync]]
  */
object Scanamo {

  /**
    * Execute the operations built with [[org.scanamo.Table]], using the client
    * provided synchronously
    *
    * {{{
    * >>> import org.scanamo.auto._
    *
    * >>> case class Transport(mode: String, line: String)
    * >>> val transport = Table[Transport]("transport")
    *
    * >>> val localDb = LocalDynamoDB.v1
    * >>> val client = localDb.clientSync()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> localDb.withTable(client)("transport")('mode -> S, 'line -> S) {
    * ...   import org.scanamo.syntax._
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
  def exec[A](client: AmazonDynamoDB)(op: ScanamoOps[A]): A = op.foldMap(ScanamoInterpreters.id(client))
}
