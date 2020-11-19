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

import cats.Monad
import cats.~>
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import org.scanamo.ops._
import scala.concurrent.{ ExecutionContext, Future }

/** Interprets Scanamo operations in an asynchronous context: Scala futures.
  */
final class ScanamoAsync private (client: DynamoDbAsyncClient)(implicit ec: ExecutionContext) {

  private val interpreter = new ScanamoAsyncInterpreter(client)

  /** Execute the operations built with [[org.scanamo.Table]]
    */
  def exec[A](op: ScanamoOps[A]): Future[A] = op.foldMap(interpreter)

  /** Execute the operations built with [[org.scanamo.Table]] with
    * effects in the monad `M` threaded in.
    */
  def execT[M[_]: Monad, A](hoist: Future ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object ScanamoAsync {
  def apply(client: DynamoDbAsyncClient)(implicit ec: ExecutionContext): ScanamoAsync = new ScanamoAsync(client)
}
