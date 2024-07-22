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

import cats.~>
import org.scanamo.ops.*
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import zio.IO
import zio.interop.catz.*
import zio.stream.{ Stream, ZStream }

class ScanamoZio private (client: DynamoDbAsyncClient)
    extends ScanamoClient(new AsyncFrameworks.Interpreter(client, ZioSpecific))

object ScanamoZio {
  type DIO[+A] = IO[DynamoDbException, A]

  def apply(client: DynamoDbAsyncClient): ScanamoZio = new ScanamoZio(client)

  val ToStream: IO[DynamoDbException, *] ~> Stream[DynamoDbException, *] =
    new (IO[DynamoDbException, *] ~> Stream[DynamoDbException, *]) {
      def apply[A](fa: IO[DynamoDbException, A]): Stream[DynamoDbException, A] = ZStream.fromEffect(fa)
    }
}
