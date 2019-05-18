package org.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.scanamo.ops._

/**
  * Provides a simplified interface for reading and writing case classes to DynamoDB
  *
  * To avoid blocking, use [[org.scanamo.ScanamoAsync]]
  */
class Scanamo(client: AmazonDynamoDB) {

  private final val interpreter = new ScanamoSyncInterpreter(client)

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
    * >>> val client = LocalDynamoDB.client()
    * >>> val scanamo = new Scanamo(client)
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTable(client)("transport")('mode -> S, 'line -> S) {
    * ...   import org.scanamo.syntax._
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central")))
    * ...     results <- transport.query('mode -> "Underground" and ('line beginsWith "C"))
    * ...   } yield results.toList
    * ...   scanamo.exec(operations)
    * ... }
    * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
    * }}}
    */
  def exec[A](op: ScanamoOps[A]): A = op.foldMap(interpreter)
}
