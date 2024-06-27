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

import cats.*
import cats.syntax.either.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{ ConditionalCheckFailedException, TransactionCanceledException }

/** Interpret Scanamo operations using blocking requests to DynamoDB with any transport errors or semantic errors within
  * DynamoDB thrown as exceptions.
  */
class ScanamoSyncInterpreter(client: DynamoDbClient) extends (ScanamoOpsA ~> Id) {
  def apply[A](op: ScanamoOpsA[A]): Id[A] =
    op match {
      case Put(req) =>
        client.putItem(JavaRequests.put(req))
      case ConditionalPut(req) =>
        Either.catchOnly[ConditionalCheckFailedException] {
          client.putItem(JavaRequests.put(req))
        }
      case Get(req) =>
        client.getItem(req)
      case Delete(req) =>
        client.deleteItem(JavaRequests.delete(req))
      case ConditionalDelete(req) =>
        Either.catchOnly[ConditionalCheckFailedException] {
          client.deleteItem(JavaRequests.delete(req))
        }
      case Scan(req) =>
        client.scan(JavaRequests.scan(req))
      case Query(req) =>
        client.query(JavaRequests.query(req))
      case BatchWrite(req) =>
        client.batchWriteItem(req)
      case BatchGet(req) =>
        client.batchGetItem(req)
      case Update(req) =>
        client.updateItem(JavaRequests.update(req))
      case ConditionalUpdate(req) =>
        Either.catchOnly[ConditionalCheckFailedException] {
          client.updateItem(JavaRequests.update(req))
        }
      case TransactWriteAll(req) =>
        Either.catchOnly[TransactionCanceledException] {
          client.transactWriteItems(JavaRequests.transactItems(req))
        }
    }
}
