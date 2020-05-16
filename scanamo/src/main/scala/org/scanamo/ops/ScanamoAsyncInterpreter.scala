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
import cats.syntax.either._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{ Put => _, Get => _, Delete => _, Update => _, _ }

import scala.concurrent.{ ExecutionContext, Future }
import scala.compat.java8.FutureConverters._

/*
 * Interpret Scanamo operations into a `Future` using the DynamoDbClient client
 * which doesn't block, using it's own thread pool for I/O requests internally
 */
class ScanamoAsyncInterpreter(client: DynamoDbAsyncClient)(implicit ec: ExecutionContext)
    extends (ScanamoOpsA ~> Future) {
  override def apply[A](op: ScanamoOpsA[A]): Future[A] = op match {
    case Put(req) => client.putItem(JavaRequests.put(req)).toScala
    case ConditionalPut(req) =>
      client
        .putItem(JavaRequests.put(req))
        .toScala
        .map(Either.right[ConditionalCheckFailedException, PutItemResponse])
        .recover {
          case e: ConditionalCheckFailedException => Either.left(e)
        }
    case Get(req)    => client.getItem(req).toScala
    case Delete(req) => client.deleteItem(JavaRequests.delete(req)).toScala
    case ConditionalDelete(req) =>
      client
        .deleteItem(JavaRequests.delete(req))
        .toScala
        .map(Either.right[ConditionalCheckFailedException, DeleteItemResponse](_))
        .recover { case e: ConditionalCheckFailedException => Either.left(e) }
    case Scan(req)  => client.scan(JavaRequests.scan(req)).toScala
    case Query(req) => client.query(JavaRequests.query(req)).toScala
    // Overloading means we need explicit parameter types here
    case BatchWrite(req) => client.batchWriteItem(req).toScala
    case BatchGet(req)   => client.batchGetItem(req).toScala
    case Update(req)     => client.updateItem(JavaRequests.update(req)).toScala
    case ConditionalUpdate(req) =>
      client
        .updateItem(JavaRequests.update(req))
        .toScala
        .map(Either.right[ConditionalCheckFailedException, UpdateItemResponse](_))
        .recover {
          case e: ConditionalCheckFailedException => Either.left(e)
        }
    case TransactWriteAll(req) => client.transactWriteItems(JavaRequests.transactItems(req)).toScala
  }
}
