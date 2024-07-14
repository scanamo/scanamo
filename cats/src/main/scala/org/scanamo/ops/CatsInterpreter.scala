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
import cats.syntax.applicative.*
import cats.syntax.applicativeError.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.option.*
import org.scanamo.ops.ScanamoOps.{Conditional, Transact}

import java.util.concurrent.CompletableFuture
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import java.util.concurrent.CompletionException
import software.amazon.awssdk.services.dynamodb.model.{ConditionalCheckFailedException, TransactionCanceledException}

class CatsInterpreter[F[_]](client: DynamoDbAsyncClient)(implicit F: Async[F]) extends (ScanamoOpsA ~> F) {
  final private def eff[A](fut: => CompletableFuture[A]): F[A] =
    F.async { cb =>
      lazy val materialised = fut
      materialised.handle[Unit] { (a, x) =>
        if (a == null)
          x match {
            case t: CompletionException => cb(Left(t.getCause))
            case t                      => cb(Left(t))
          }
        else
          cb(Right(a))
      }
      F.delay(materialised.cancel(false)).void.some.pure[F]
    }

  private def effWithExposedException[T, ExposedEx](value: CompletableFuture[T])(rF: PartialFunction[Throwable, ExposedEx]): F[Either[ExposedEx, T]] = {
    eff(value).attempt.flatMap(
      _.fold(
        e => rF.andThen(exposed => F.delay[Either[ExposedEx, T]](Left(exposed))).applyOrElse(e, F.raiseError),
        a => F.delay(Right(a))
      )
    )
  }

  private def effConditional[T](value: CompletableFuture[T]): F[Conditional[T]] =
    effWithExposedException(value) { case e: ConditionalCheckFailedException => e }

  private def effTransact[T](value: CompletableFuture[T]): F[Transact[T]] =
    effWithExposedException(value) { case e: TransactionCanceledException => e }

  override def apply[A](fa: ScanamoOpsA[A]): F[A] = fa match {
    case Put(req) => eff(client.putItem(JavaRequests.put(req)))
    case ConditionalPut(req) => effConditional(client.putItem(JavaRequests.put(req)))
    case Get(req) => eff(client.getItem(req))
    case Delete(req) => eff(client.deleteItem(JavaRequests.delete(req)))
    case ConditionalDelete(req) => effConditional(client.deleteItem(JavaRequests.delete(req)))
    case Scan(req) => eff(client.scan(JavaRequests.scan(req)))
    case Query(req) => eff(client.query(JavaRequests.query(req)))
    case BatchWrite(req) => eff(client.batchWriteItem(req))
    case BatchGet(req) => eff(client.batchGetItem(req))
    case Update(req) => eff(client.updateItem(JavaRequests.update(req)))
    case ConditionalUpdate(req) => effConditional(client.updateItem(JavaRequests.update(req)))
    case TransactWriteAll(req) => effTransact(client.transactWriteItems(JavaRequests.transactItems(req)))
  }

}
