package org.scanamo.ops

import java.util.concurrent.ScheduledExecutorService

import akka.stream.alpakka.dynamodb.AwsOp
import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import cats.syntax.either._
import cats.~>
import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.ops.retrypolicy._

import scala.concurrent.{ExecutionContext, Future}

object AlpakkaInterpreter {

  def future(client: DynamoClient, retryPolicy: RetryPolicy)(
    implicit ec: ExecutionContext,
    scheduler: ScheduledExecutorService
  ): ScanamoOpsA ~> Future =
    new (ScanamoOpsA ~> Future) {
      import akka.stream.alpakka.dynamodb.scaladsl.DynamoImplicits._

      override def apply[A](ops: ScanamoOpsA[A]) =
        ops match {
          case Put(req)        => executeSingleRequest(client, JavaRequests.put(req), retryPolicy)
          case Get(req)        => executeSingleRequest(client, req, retryPolicy)
          case Delete(req)     => executeSingleRequest(client, JavaRequests.delete(req), retryPolicy)
          case Scan(req)       => executeSingleRequest(client, JavaRequests.scan(req), retryPolicy)
          case Query(req)      => executeSingleRequest(client, JavaRequests.query(req), retryPolicy)
          case Update(req)     => executeSingleRequest(client, JavaRequests.update(req), retryPolicy)
          case BatchWrite(req) => executeSingleRequest(client, req, retryPolicy)
          case BatchGet(req)   => executeSingleRequest(client, req, retryPolicy)
          case ConditionalDelete(req) =>
            executeSingleRequest(client, JavaRequests.delete(req), retryPolicy)
              .map(Either.right[ConditionalCheckFailedException, DeleteItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
          case ConditionalPut(req) =>
            executeSingleRequest(client, JavaRequests.put(req), retryPolicy)
              .map(Either.right[ConditionalCheckFailedException, PutItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
          case ConditionalUpdate(req) =>
            executeSingleRequest(client, JavaRequests.update(req), retryPolicy)
              .map(Either.right[ConditionalCheckFailedException, UpdateItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
        }
    }

  private def executeSingleRequest(
    dynamoClient: DynamoClient,
    op: AwsOp,
    retryPolicy: RetryPolicy
  )(implicit ec: ExecutionContext, scheduler: ScheduledExecutorService) = {
    def future() = dynamoClient.single(op)
    RetryUtility.retryWithBackOff(future(), retryPolicy)
  }
}
