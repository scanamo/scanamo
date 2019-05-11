package org.scanamo.ops

import akka.stream.alpakka.dynamodb.AwsOp
import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import cats.syntax.either._
import cats.~>
import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.ops.retrypolicy._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object AlpakkaInterpreter {

  def future(client: DynamoClient, retryPolicy: RetryPolicy)(
    implicit ec: ExecutionContext
  ): ScanamoOpsA ~> Future =
    new (ScanamoOpsA ~> Future) {
      import akka.stream.alpakka.dynamodb.scaladsl.DynamoImplicits._
      implicit final val retry: RetryPolicy = retryPolicy

      override def apply[A](ops: ScanamoOpsA[A]) =
        ops match {
          case Put(req) => executeSingleRequest(client, JavaRequests.put(req))
          case Get(req) => executeSingleRequest(client, req)
          case Delete(req) =>
            executeSingleRequest(client, JavaRequests.delete(req))
          case Scan(req) => executeSingleRequest(client, JavaRequests.scan(req))
          case Query(req) =>
            executeSingleRequest(client, JavaRequests.query(req))
          case Update(req) =>
            executeSingleRequest(client, JavaRequests.update(req))
          case BatchWrite(req) => executeSingleRequest(client, req)
          case BatchGet(req)   => executeSingleRequest(client, req)
          case ConditionalDelete(req) =>
            executeSingleRequest(client, JavaRequests.delete(req))
              .map(
                Either.right[ConditionalCheckFailedException, DeleteItemResult]
              )
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
          case ConditionalPut(req) =>
            executeSingleRequest(client, JavaRequests.put(req))
              .map(Either.right[ConditionalCheckFailedException, PutItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
          case ConditionalUpdate(req) =>
            executeSingleRequest(client, JavaRequests.update(req))
              .map(
                Either.right[ConditionalCheckFailedException, UpdateItemResult]
              )
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
        }
    }

  private def executeSingleRequest(
    dynamoClient: DynamoClient,
    op: AwsOp
  )(implicit ec: ExecutionContext, retryPolicy: RetryPolicy) = {
    val selectedRetryPolicy = DefaultRetryPolicy.getPolicy(retryPolicy)
    def future() = dynamoClient.single(op)
    RetryUtility.retryWithBackOff(future, selectedRetryPolicy)
  }
}
