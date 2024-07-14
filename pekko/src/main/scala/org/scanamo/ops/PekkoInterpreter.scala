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

import cats.syntax.either.*
import cats.~>
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.stream.connectors.dynamodb.{DynamoDbOp, DynamoDbPaginatedOp}
import org.apache.pekko.stream.connectors.dynamodb.scaladsl.DynamoDb
import org.apache.pekko.stream.scaladsl.Source
import org.scanamo.ops.ScanamoOps.{Conditional, Transact}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{BatchGetItemRequest, BatchGetItemResponse, BatchWriteItemRequest, BatchWriteItemResponse, ConditionalCheckFailedException, DeleteItemRequest, DeleteItemResponse, DynamoDbRequest, DynamoDbResponse, GetItemRequest, GetItemResponse, PutItemRequest, PutItemResponse, QueryRequest, QueryResponse, ScanRequest, ScanResponse, TransactionCanceledException, UpdateItemRequest, UpdateItemResponse}

import java.util.concurrent.CompletionException

/** This is a port of [[https://github.com/scanamo/scanamo/pull/151 AlpakkaInterpreter]], which has since been removed
  * from the core Scanamo project.
  */
private[scanamo] class PekkoInterpreter(implicit client: DynamoDbAsyncClient, system: ClassicActorSystemProvider)
    extends (ScanamoOpsA ~> PekkoInterpreter.Pekko) {

  private[this] val unwrap: PartialFunction[Throwable, Throwable] = { case error: CompletionException =>
    error.getCause
  }

  final private def run[In <: DynamoDbRequest, Out <: DynamoDbResponse](
    op: In
  )(implicit operation: DynamoDbOp[In, Out]): PekkoInterpreter.Pekko[Out] =
    Source.future(DynamoDb.single(op)).mapError(unwrap)

  final private def runPaginated[In <: DynamoDbRequest, Out <: DynamoDbResponse](
    op: In
  )(implicit operation: DynamoDbPaginatedOp[In, Out, _]): PekkoInterpreter.Pekko[Out] =
    DynamoDb.source(op).mapError(unwrap)

  def runAndExposeException[In <: DynamoDbRequest, Out <: DynamoDbResponse, ExposedEx](op: In)(rF: PartialFunction[Throwable, ExposedEx])(implicit operation: DynamoDbOp[In, Out]): Source[Either[ExposedEx, Out], NotUsed] =
    run(op).map(Either.right[ExposedEx, Out]).recover(rF.andThen(Either.left))

  def runConditional[In <: DynamoDbRequest, Out <: DynamoDbResponse](op: In)(implicit o: DynamoDbOp[In, Out]): Source[Conditional[Out], NotUsed] =
    runAndExposeException(op) { case e: ConditionalCheckFailedException => e }

  def runTransact[In <: DynamoDbRequest, Out <: DynamoDbResponse](op: In)(implicit o: DynamoDbOp[In, Out]): Source[Transact[Out], NotUsed] =
    runAndExposeException(op) { case e: TransactionCanceledException => e }

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
      case ConditionalDelete(req) => runConditional(JavaRequests.delete(req))
      case ConditionalPut(req) => runConditional(JavaRequests.put(req))
      case ConditionalUpdate(req) => runConditional(JavaRequests.update(req))
      case TransactWriteAll(req) => runTransact(JavaRequests.transactItems(req))
    }
}

object PekkoInterpreter {
  type Pekko[A] = Source[A, NotUsed]
}
