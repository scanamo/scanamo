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

import cats.syntax.either._

import eu.timepit.refined.api.{ RefType, Validate }

package object refined {
  implicit def refTypeDynamoFormat[F[_, _], T, P](implicit
    baseFormat: DynamoFormat[T],
    refType: RefType[F],
    validate: Validate[T, P]
  ): DynamoFormat[F[T, P]] =
    new DynamoFormat[F[T, P]] {
      final def read(av: DynamoValue): DynamoFormat.Result[F[T, P]] =
        baseFormat.read(av).flatMap(v => refType.refine[P](v).leftMap(desc => TypeCoercionError(new Exception(desc))))

      final def write(v: F[T, P]): DynamoValue =
        baseFormat.write(refType.unwrap(v))
    }
}
