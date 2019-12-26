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

package org.scanamo.generic

import magnolia.Magnolia
import org.scanamo.DynamoFormat
import scala.language.experimental.macros

final private case class Hidden[A](instance: A) extends AnyVal

trait SemiautoDerivation extends Derivation {
  type Typeclass[A] = HiddenDynamoFormat[A]

  final protected def build[A](df: DynamoFormat[A]): Typeclass[A] = Hidden(df)

  final protected def unbuild[A](tc: Typeclass[A]): DynamoFormat[A] = tc.instance

  final implicit def hiddenDynamoFormat[A]: HiddenDynamoFormat[A] = macro Magnolia.gen[A]

  final def deriveDynamoFormat[A](implicit S: HiddenDynamoFormat[A]): DynamoFormat[A] = S.instance
}
