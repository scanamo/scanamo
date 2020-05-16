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

import cats.effect.Async
import cats.implicits._
import cats.~>
import software.amazon.awssdk.services.dynamodb.model.DynamoDbRequest
import com.amazonaws.handlers.AsyncHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{ Put => _, Delete => _, Update => _, Get => _, _ }

class CatsInterpreter[F[_]](client: DynamoDbAsyncClient)(implicit F: Async[F]) extends (ScanamoOpsA ~> F) {
  final private def eff[A <: DynamoDbRequest, B](
    f: (A, AsyncHandler[A, B]) => java.util.concurrent.Future[B],
    req: A
  ): F[B] =
    F.async { cb =>
      val handler = new AsyncHandler[A, B] {
        def onError(exception: Exception): Unit =
          cb(Left(exception))

        def onSuccess(request: A, result: B): Unit =
          cb(Right(result))
      }
      val _ = f(req, handler)
    }

  override def apply[A](fa: ScanamoOpsA[A]): F[A] = fa match {
    case Put(req) =>
      eff(client.putItemAsync, JavaRequests.put(req))
    case ConditionalPut(req) =>
      eff(client.putItemAsync, JavaRequests.put(req)).attempt
        .flatMap(
          _.fold(
            _ match {
              case e: ConditionalCheckFailedException => F.delay(Left(e))
              case t                                  => F.raiseError(t) // raise error as opposed to swallowing
            },
            a => F.delay(Right(a))
          )
        )
    case Get(req) =>
      eff(client.getItemAsync, req)
    case Delete(req) =>
      eff(client.deleteItemAsync, JavaRequests.delete(req))
    case ConditionalDelete(req) =>
      eff(client.deleteItemAsync, JavaRequests.delete(req)).attempt
        .flatMap(
          _.fold(
            _ match {
              case e: ConditionalCheckFailedException => F.delay(Left(e))
              case t                                  => F.raiseError(t) // raise error as opposed to swallowing
            },
            a => F.delay(Right(a))
          )
        )
    case Scan(req) =>
      eff(client.scanAsync, JavaRequests.scan(req))
    case Query(req) =>
      eff(client.queryAsync, JavaRequests.query(req))
    // Overloading means we need explicit parameter types here
    case BatchWrite(req) =>
      eff(
        client.batchWriteItemAsync(
          _: BatchWriteItemRequest,
          _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]
        ),
        req
      )
    case BatchGet(req) =>
      eff(
        client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]),
        req
      )
    case Update(req) =>
      eff(client.updateItemAsync, JavaRequests.update(req))
    case ConditionalUpdate(req) =>
      eff(client.updateItemAsync, JavaRequests.update(req)).attempt
        .flatMap(
          _.fold(
            _ match {
              case e: ConditionalCheckFailedException => F.delay(Left(e))
              case t                                  => F.raiseError(t) // raise error as opposed to swallowing
            },
            a => F.delay(Right(a))
          )
        )
    case TransactWriteAll(req) => eff(client.transactWriteItemsAsync, JavaRequests.transactItems(req))
  }
}
