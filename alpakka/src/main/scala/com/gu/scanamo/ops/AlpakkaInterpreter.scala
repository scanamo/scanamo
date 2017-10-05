package com.gu.scanamo.ops

import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import cats.~>
import cats.syntax.either._
import com.amazonaws.services.dynamodbv2.model._

import scala.concurrent.{ExecutionContext, Future}

object AlpakkaInterpreter {

  def future(client: DynamoClient)(implicit executor: ExecutionContext): ScanamoOpsA ~> Future =
    new (ScanamoOpsA ~> Future) {
      import akka.stream.alpakka.dynamodb.scaladsl.DynamoImplicits._

      override def apply[A](ops: ScanamoOpsA[A]) = {
        ops match {
          case Put(req) => client.single(JavaRequests.put(req))
          case Get(req) => client.single(req)
          case Delete(req) => client.single(JavaRequests.delete(req))
          case Scan(req) => client.single(JavaRequests.scan(req))
          case Query(req) => client.single(JavaRequests.query(req))
          case Update(req) => client.single(JavaRequests.update(req))
          case BatchWrite(req) => client.single(req)
          case BatchGet(req) => client.single(req)
          case ConditionalDelete(req) =>
            client.single(JavaRequests.delete(req))
              .map(Either.right[ConditionalCheckFailedException, DeleteItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
          case ConditionalPut(req) =>
            client.single(JavaRequests.put(req))
              .map(Either.right[ConditionalCheckFailedException, PutItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
          case ConditionalUpdate(req) =>
            client.single(JavaRequests.update(req))
              .map(Either.right[ConditionalCheckFailedException, UpdateItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
        }
      }
    }

}