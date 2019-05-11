package org.scanamo.ops

import akka.stream.alpakka.dynamodb.AwsOp
import akka.stream.alpakka.dynamodb.scaladsl.DynamoClient
import cats.syntax.either._
import cats.~>
import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.ops.retrypolicy.DefaultRetryPolicy
import org.scanamo.ops.retrypolicy.RetryPolicy
import org.scanamo.ops.retrypolicy.RetryUtility

import scala.concurrent.{ ExecutionContext, Future }

object AlpakkaInterpreter {

  def future(client: DynamoClient, retrySettings: RetryPolicy)(implicit executor: ExecutionContext): ScanamoOpsA ~> Future =
    new (ScanamoOpsA ~> Future) {
      import akka.stream.alpakka.dynamodb.scaladsl.DynamoImplicits._

      override def apply[A](ops: ScanamoOpsA[A]) =
        ops match {
          case Put(req)        => executeSingleRequest(client, JavaRequests.put(req), retrySettings)
          case Get(req)        => executeSingleRequest(client, req, retrySettings)
          case Delete(req)     => executeSingleRequest(client, JavaRequests.delete(req), retrySettings)
          case Scan(req)       => executeSingleRequest(client, JavaRequests.scan(req), retrySettings)
          case Query(req)      => executeSingleRequest(client, JavaRequests.query(req), retrySettings)
          case Update(req)     => executeSingleRequest(client, JavaRequests.update(req), retrySettings)
          case BatchWrite(req) => executeSingleRequest(client, req, retrySettings)
          case BatchGet(req)   => executeSingleRequest(client, req, retrySettings)
          case ConditionalDelete(req) =>
            executeSingleRequest(client, JavaRequests.delete(req), retrySettings)
              .map(Either.right[ConditionalCheckFailedException, DeleteItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
          case ConditionalPut(req) =>
            executeSingleRequest(client, JavaRequests.put(req), retrySettings)
              .map(Either.right[ConditionalCheckFailedException, PutItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
          case ConditionalUpdate(req) =>
            executeSingleRequest(client, JavaRequests.update(req), retrySettings)
              .map(Either.right[ConditionalCheckFailedException, UpdateItemResult])
              .recover {
                case e: ConditionalCheckFailedException => Either.left(e)
              }
        }
    }

   def executeSingleRequest(client: DynamoClient, op: AwsOp, retryPolicy: RetryPolicy)(implicit executor: ExecutionContext) = {
    def future() = client.single(op)
    val exponentialRetryPolicy = DefaultRetryPolicy.getPolicy(retryPolicy)
    RetryUtility
      .retryWithBackOff(future(), exponentialRetryPolicy)
  }
}
