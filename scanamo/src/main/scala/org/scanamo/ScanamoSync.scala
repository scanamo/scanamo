package org.scanamo

import cats.{ ~>, Id, Monad }
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.scanamo.ops._

/**
  * Provides a simplified interface for reading and writing case classes to DynamoDB
  *
  * To avoid blocking, use [[org.scanamo.ScanamoAsync]]
  */
class Scanamo private (client: AmazonDynamoDB) {

  final private val interpreter = new ScanamoSyncInterpreter(client)

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
    * >>> val scanamo = Scanamo(client)
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTable(client)("transport")("mode" -> S, "line" -> S) {
    * ...   import org.scanamo.syntax._
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
  final def exec[A](op: ScanamoOps[A]): A = op.foldMap(interpreter)

  final def execT[M[_]: Monad, A](hoist: Id ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object Scanamo {
  def apply(client: AmazonDynamoDB): Scanamo = new Scanamo(client)

  val IdToList: Id ~> List = new (Id ~> List) {
    def apply[A](fa: Id[A]): List[A] = fa :: Nil
  }

  val IdToStream: Id ~> Stream = new (Id ~> Stream) {
    def apply[A](fa: Id[A]): Stream[A] = Stream(fa)
  }
}
