package org.scanamo.ops

import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import cats.syntax.either._
import cats.~>
import com.amazonaws.services.dynamodbv2.{model => v1}
import com.gu.scanamo.ops.aws._
import software.amazon.awssdk.services.dynamodb.model._

import scala.concurrent.{ExecutionContext, Future}

object AlpakkaInterpreter {

  def future(client: DynamoClient)(implicit executor: ExecutionContext): ScanamoOpsA ~> Future =
    new (ScanamoOpsA ~> Future) {
      import akka.stream.alpakka.dynamodb.scaladsl.DynamoImplicits._

      override def apply[A](ops: ScanamoOpsA[A]) =
        ops match {
          case Put(req)        => client.single(JavaRequests.put(req).legacy).map(_.fromLegacy)
          case Get(req)        => client.single(req.legacy).map(_.fromLegacy)
          case Delete(req)     => client.single(JavaRequests.delete(req).legacy).map(_.fromLegacy)
          case Scan(req)       => client.single(JavaRequests.scan(req).legacy).map(_.fromLegacy)
          case Query(req)      => client.single(JavaRequests.query(req).legacy).map(_.fromLegacy)
          case Update(req)     => client.single(JavaRequests.update(req).legacy).map(_.fromLegacy)
          case BatchGet(req)   => client.single(req.legacy).map(_.fromLegacy)
          case BatchWrite(req) => client.single(req.legacy).map(_.fromLegacy)
          case ConditionalDelete(req) =>
            client
              .single(JavaRequests.delete(req).legacy)
              .map[Either[ConditionalCheckFailedException, DeleteItemResponse]](i => Right(i.fromLegacy))
              .recover {
                case e: v1.ConditionalCheckFailedException => Either.left(e.fromLegacy)
              }
          case ConditionalPut(req) =>
            client
              .single(JavaRequests.put(req).legacy)
              .map[Either[ConditionalCheckFailedException, PutItemResponse]](i => Right(i.fromLegacy))
              .recover {
                case e: v1.ConditionalCheckFailedException => Either.left(e.fromLegacy)
              }
          case ConditionalUpdate(req) =>
            client
              .single(JavaRequests.update(req).legacy)
              .map[Either[ConditionalCheckFailedException, UpdateItemResponse]](i => Right(i.fromLegacy))
              .recover {
                case e: v1.ConditionalCheckFailedException => Either.left(e.fromLegacy)
              }
        }
    }

}
