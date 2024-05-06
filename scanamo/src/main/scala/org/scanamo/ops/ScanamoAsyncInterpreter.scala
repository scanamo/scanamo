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

import cats._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{ Delete => _, Get => _, Put => _, Update => _, _ }

import java.util.concurrent.{ CompletionException, CompletionStage }
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }

/*
 * Interpret Scanamo operations into a `Future` using the DynamoDbClient client
 * which doesn't block, using it's own thread pool for I/O requests internally
 */
class ScanamoAsyncInterpreter(client: DynamoDbAsyncClient)(implicit ec: ExecutionContext)
    extends (ScanamoOpsA ~> Future) {

  private def run[A](completionStage: CompletionStage[A]): Future[A] =
    completionStage.toScala.recoverWith { case error: CompletionException => Future.failed(error.getCause) }

  final private def runEitherConditionalCheckFailed[A](
    completionStage: CompletionStage[A]
  ): Future[Either[ConditionalCheckFailedException, A]] =
    run(completionStage).map(Right(_)).recover { case e: ConditionalCheckFailedException => Left(e) }

  final private def runEitherTransactionCanceledFailed[A](
    completionStage: CompletionStage[A]
  ): Future[Either[TransactionCanceledException, A]] =
    run(completionStage).map(Right(_)).recover { case e: TransactionCanceledException => Left(e) }

  override def apply[A](op: ScanamoOpsA[A]): Future[A] =
    op match {
      case Put(req) =>
        run(client.putItem(JavaRequests.put(req)))
      case ConditionalPut(req) =>
        runEitherConditionalCheckFailed(client.putItem(JavaRequests.put(req)))
      case Get(req) =>
        run(client.getItem(req))
      case Delete(req) =>
        run(client.deleteItem(JavaRequests.delete(req)))
      case ConditionalDelete(req) =>
        runEitherConditionalCheckFailed(client.deleteItem(JavaRequests.delete(req)))
      case Scan(req) =>
        run(client.scan(JavaRequests.scan(req)))
      case Query(req) =>
        run(client.query(JavaRequests.query(req)))
      case BatchWrite(req) =>
        run(client.batchWriteItem(req))
      case BatchGet(req) =>
        run(client.batchGetItem(req))
      case Update(req) =>
        run(client.updateItem(JavaRequests.update(req)))
      case ConditionalUpdate(req) =>
        runEitherConditionalCheckFailed(client.updateItem(JavaRequests.update(req)))
      case TransactWriteAll(req) =>
        runEitherTransactionCanceledFailed(client.transactWriteItems(JavaRequests.transactItems(req)))
    }
}
