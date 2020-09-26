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
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{ Put => _, Get => _, Delete => _, Update => _, _ }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.dynamodb.{ DynamoDbOp, DynamoDbPaginatedOp }
import akka.stream.alpakka.dynamodb.scaladsl.DynamoDb
import akka.stream.scaladsl.Source

private[scanamo] class AlpakkaInterpreter(implicit client: DynamoDbAsyncClient, mat: Materializer)
    extends (ScanamoOpsA ~> AlpakkaInterpreter.Alpakka) {

  final private def run[In <: DynamoDbRequest, Out <: DynamoDbResponse](
    op: In
  )(implicit operation: DynamoDbOp[In, Out]): AlpakkaInterpreter.Alpakka[Out] =
    Source.fromFuture(DynamoDb.single(op))

  final private def runPaginated[In <: DynamoDbRequest, Out <: DynamoDbResponse](
    op: In
  )(implicit operation: DynamoDbPaginatedOp[In, Out, _]): AlpakkaInterpreter.Alpakka[Out] =
    DynamoDb.source(op)

  def apply[A](ops: ScanamoOpsA[A]) =
    ops match {
      case Put(req)        => run[PutItemRequest, PutItemResponse](JavaRequests.put(req))
      case Get(req)        => run[GetItemRequest, GetItemResponse](req)
      case Delete(req)     => run[DeleteItemRequest, DeleteItemResponse](JavaRequests.delete(req))
      case Scan(req)       => runPaginated[ScanRequest, ScanResponse](JavaRequests.scan(req))
      case Query(req)      => runPaginated[QueryRequest, QueryResponse](JavaRequests.query(req))
      case Update(req)     => run[UpdateItemRequest, UpdateItemResponse](JavaRequests.update(req))
      case BatchWrite(req) => run[BatchWriteItemRequest, BatchWriteItemResponse](req)
      case BatchGet(req)   => run[BatchGetItemRequest, BatchGetItemResponse](req)
      case ConditionalDelete(req) =>
        run(JavaRequests.delete(req))
          .map(Either.right[ConditionalCheckFailedException, DeleteItemResponse])
          .recover { case e: ConditionalCheckFailedException =>
            Either.left(e)
          }
      case ConditionalPut(req) =>
        run(JavaRequests.put(req))
          .map(Either.right[ConditionalCheckFailedException, PutItemResponse])
          .recover { case e: ConditionalCheckFailedException =>
            Either.left(e)
          }
      case ConditionalUpdate(req) =>
        run(JavaRequests.update(req))
          .map(Either.right[ConditionalCheckFailedException, UpdateItemResponse])
          .recover { case e: ConditionalCheckFailedException =>
            Either.left(e)
          }
      case TransactWriteAll(req) =>
        run[TransactWriteItemsRequest, TransactWriteItemsResponse](JavaRequests.transactItems(req))
    }
}

object AlpakkaInterpreter {
  type Alpakka[A] = Source[A, NotUsed]
}
