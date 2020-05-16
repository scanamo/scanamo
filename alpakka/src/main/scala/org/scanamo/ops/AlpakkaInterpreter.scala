/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo.ops

import cats.~>
import cats.syntax.either._
import software.amazon.awssdk.services.dynamodb.model.{ Put => _, Get => _, Delete => _, Update => _, _ }
import org.scanamo.ops.retrypolicy._

import akka.stream.alpakka.dynamodb.{ AwsOp, AwsPagedOp, DynamoAttributes, DynamoClient }
import akka.stream.alpakka.dynamodb.scaladsl.DynamoDb
import akka.stream.scaladsl.Source
import akka.NotUsed

private[scanamo] class AlpakkaInterpreter(client: DynamoClient,
                                          retryPolicy: RetryPolicy,
                                          isRetryable: Throwable => Boolean)
    extends (ScanamoOpsA ~> AlpakkaInterpreter.Alpakka)
    with WithRetry {
  override def retryable(throwable: Throwable): Boolean = isRetryable(throwable)

  final private def run(op: AwsOp): AlpakkaInterpreter.Alpakka[op.B] =
    retry(DynamoDb.source(op).withAttributes(DynamoAttributes.client(client)), retryPolicy)

  def apply[A](ops: ScanamoOpsA[A]) =
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
        run(JavaRequests.delete(req))
          .map(Either.right[ConditionalCheckFailedException, DeleteItemResponse])
          .recover {
            case e: ConditionalCheckFailedException => Either.left(e)
          }
      case ConditionalPut(req) =>
        run(JavaRequests.put(req))
          .map(Either.right[ConditionalCheckFailedException, PutItemResponse])
          .recover {
            case e: ConditionalCheckFailedException => Either.left(e)
          }
      case ConditionalUpdate(req) =>
        run(JavaRequests.update(req))
          .map(Either.right[ConditionalCheckFailedException, UpdateItemResult])
          .recover {
            case e: ConditionalCheckFailedException => Either.left(e)
          }
      case TransactWriteAll(req) => run(JavaRequests.transactItems(req))
    }
}

object AlpakkaInterpreter {
  type Alpakka[A] = Source[A, NotUsed]
}
