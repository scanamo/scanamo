package org.scanamo.ops

import cats.~>
import cats.syntax.either._
import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.ops.retrypolicy._

import akka.stream.alpakka.dynamodb.{AwsOp, AwsPagedOp, DynamoAttributes, DynamoClient}
import akka.stream.alpakka.dynamodb.scaladsl.DynamoDb
import akka.stream.scaladsl.Source
import akka.NotUsed

object AlpakkaInterpreter extends WithRetry {

  type Alpakka[A] = Source[A, NotUsed]

  def future(client: DynamoClient, retryPolicy: RetryPolicy): ScanamoOpsA ~> Alpakka =
    new (ScanamoOpsA ~> Alpakka) {
      private final def run(op: AwsOp): Alpakka[op.B] =
        retry(DynamoDb.source(op).withAttributes(DynamoAttributes.client(client)), retryPolicy)

      override def apply[A](ops: ScanamoOpsA[A]) =
        ops match {
          case Put(req)        => run(JavaRequests.put(req))
          case Get(req)        => run(req)
          case Delete(req)     => run(JavaRequests.delete(req))
          case Scan(req)       => run(AwsPagedOp.create(JavaRequests.scan(req)))
          case Query(req)      => run(AwsPagedOp.create(JavaRequests.query(req)))
          case Update(req)     => run(JavaRequests.update(req))
          case BatchWrite(req) => run(req)
          case BatchGet(req)   => run(req)
          case ConditionalDelete(req) =>
            run(JavaRequests.delete(req)).map(Either.right[ConditionalCheckFailedException, DeleteItemResult]).recover {
              case e: ConditionalCheckFailedException => Either.left(e)
            }
          case ConditionalPut(req) =>
            run(JavaRequests.put(req)).map(Either.right[ConditionalCheckFailedException, PutItemResult]).recover {
              case e: ConditionalCheckFailedException => Either.left(e)
            }
          case ConditionalUpdate(req) =>
            run(JavaRequests.update(req)).map(Either.right[ConditionalCheckFailedException, UpdateItemResult]).recover {
              case e: ConditionalCheckFailedException => Either.left(e)
            }
        }
    }
}
