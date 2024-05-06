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
import java.util.concurrent.CompletableFuture
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{ Delete => _, Get => _, Put => _, Update => _, _ }
import zio.{ IO, ZIO }

private[scanamo] class ZioInterpreter(client: DynamoDbAsyncClient) extends (ScanamoOpsA ~> IO[DynamoDbException, *]) {
  final private def eff[A](fut: => CompletableFuture[A]): IO[DynamoDbException, A] =
    ZIO.fromCompletionStage(fut).refineToOrDie[DynamoDbException]

  final private def effEitherConditionalCheckFailed[A](
    fut: => CompletableFuture[A]
  ): IO[DynamoDbException, Either[ConditionalCheckFailedException, A]] =
    eff(fut).map(Right(_)).catchSome { case e: ConditionalCheckFailedException => IO.succeed(Left(e)) }

  final private def effEitherTransactionCanceledFailed[A](
    fut: => CompletableFuture[A]
  ): IO[DynamoDbException, Either[TransactionCanceledException, A]] =
    eff(fut).map(Right(_)).catchSome { case e: TransactionCanceledException => IO.succeed(Left(e)) }

  def apply[A](op: ScanamoOpsA[A]): IO[DynamoDbException, A] =
    op match {
      case Put(req) =>
        eff(client.putItem(JavaRequests.put(req)))
      case ConditionalPut(req) =>
        effEitherConditionalCheckFailed(client.putItem(JavaRequests.put(req)))
      case Get(req) =>
        eff(client.getItem(req))
      case Delete(req) =>
        eff(client.deleteItem(JavaRequests.delete(req)))
      case ConditionalDelete(req) =>
        effEitherConditionalCheckFailed(client.deleteItem(JavaRequests.delete(req)))
      case Scan(req) =>
        eff(client.scan(JavaRequests.scan(req)))
      case Query(req) =>
        eff(client.query(JavaRequests.query(req)))
      case BatchWrite(req) =>
        eff(client.batchWriteItem(req))
      case BatchGet(req) =>
        eff(client.batchGetItem(req))
      case Update(req) =>
        eff(client.updateItem(JavaRequests.update(req)))
      case ConditionalUpdate(req) =>
        effEitherConditionalCheckFailed(client.updateItem(JavaRequests.update(req)))
      case TransactWriteAll(req) =>
        effEitherTransactionCanceledFailed(client.transactWriteItems(JavaRequests.transactItems(req)))
    }
}
