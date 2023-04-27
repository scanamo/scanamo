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
import cats.~>
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import java.util.concurrent.CompletableFuture
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.util.concurrent.CompletionException
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException

class CatsInterpreter[F[_]](client: DynamoDbAsyncClient)(implicit F: Async[F]) extends (ScanamoOpsA ~> F) {
  final private def eff[A](fut: => CompletableFuture[A]): F[A] =
    F.async_ { cb =>
      fut.handle[Unit] { (a, x) =>
        if (a == null)
          x match {
            case t: CompletionException => cb(Left(t.getCause))
            case t                      => cb(Left(t))
          }
        else
          cb(Right(a))
      }
      ()
    }

  override def apply[A](fa: ScanamoOpsA[A]): F[A] =
    fa match {
      case Put(req) =>
        eff(client.putItem(JavaRequests.put(req)))
      case ConditionalPut(req) =>
        eff(client.putItem(JavaRequests.put(req))).attempt
          .flatMap(
            _.fold(
              {
                case e: ConditionalCheckFailedException => F.delay(Left(e))
                case t                                  => F.raiseError(t) // raise error as opposed to swallowing
              },
              a => F.delay(Right(a))
            )
          )
      case Get(req) =>
        eff(client.getItem(req))
      case Delete(req) =>
        eff(client.deleteItem(JavaRequests.delete(req)))
      case ConditionalDelete(req) =>
        eff(client.deleteItem(JavaRequests.delete(req))).attempt
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
        eff(client.scan(JavaRequests.scan(req)))
      case Query(req) =>
        eff(client.query(JavaRequests.query(req)))
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        eff(client.batchWriteItem(req))
      case BatchGet(req) =>
        eff(client.batchGetItem(req))
      case Update(req) =>
        eff(client.updateItem(JavaRequests.update(req)))
      case ConditionalUpdate(req) =>
        eff(client.updateItem(JavaRequests.update(req))).attempt
          .flatMap(
            _.fold(
              _ match {
                case e: ConditionalCheckFailedException => F.delay(Left(e))
                case t                                  => F.raiseError(t) // raise error as opposed to swallowing
              },
              a => F.delay(Right(a))
            )
          )
      case TransactWriteAll(req) =>
        eff(client.transactWriteItems(JavaRequests.transactItems(req)))
    }
}
