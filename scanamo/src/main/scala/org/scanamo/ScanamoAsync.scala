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

package org.scanamo

import org.scanamo.ops.*
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.concurrent.ExecutionContext

/** Interprets Scanamo operations in an asynchronous context: Scala futures. Using the DynamoDbClient client which
  * doesn't block, using it's own thread pool for I/O requests internally
  */
final class ScanamoAsync private (client: DynamoDbAsyncClient)(implicit ec: ExecutionContext)
    extends ScanamoClient(new AsyncFrameworks.Interpreter(client, new ScalaFutureSpecific()))

object ScanamoAsync {
  def apply(client: DynamoDbAsyncClient)(implicit ec: ExecutionContext): ScanamoAsync = new ScanamoAsync(client)
}
