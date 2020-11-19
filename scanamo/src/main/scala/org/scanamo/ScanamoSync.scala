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

import cats.{ ~>, Id, Monad }
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import org.scanamo.ops._

/** Provides a simplified interface for reading and writing case classes to DynamoDB
  *
  * To avoid blocking, use [[org.scanamo.ScanamoAsync]]
  */
final class Scanamo private (client: DynamoDbClient) {
  private val interpreter = new ScanamoSyncInterpreter(client)

  /** Execute the operations built with [[org.scanamo.Table]]
    */
  def exec[A](op: ScanamoOps[A]): A = op.foldMap(interpreter)

  /** Execute the operations built with [[org.scanamo.Table]] with
    * effects in the monad `M` threaded in.
    */
  def execT[M[_]: Monad, A](hoist: Id ~> M)(op: ScanamoOpsT[M, A]): M[A] =
    op.foldMap(interpreter andThen hoist)
}

object Scanamo {
  def apply(client: DynamoDbClient): Scanamo = new Scanamo(client)

  val ToList: Id ~> List = new (Id ~> List) {
    def apply[A](fa: Id[A]): List[A] = fa :: Nil
  }

  val ToStream: Id ~> Stream = new (Id ~> Stream) {
    def apply[A](fa: Id[A]): Stream[A] = Stream(fa)
  }
}
