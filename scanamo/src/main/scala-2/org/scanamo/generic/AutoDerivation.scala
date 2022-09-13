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

import magnolia1._

import org.scanamo.DynamoFormat
import scala.reflect.macros.whitebox

trait AutoDerivation extends Derivation {

  /** Materialize an exported format by wrapping the magnolia derivation
    * during macro expansion
    *
    * @note All credits to the inimitable @neko-kai https://github.com/propensive/magnolia/issues/107#issuecomment-589289260
    */
  def materializeImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[Exported[DynamoFormat[A]]] = {
    val magnoliaTree = c.Expr[DynamoFormat[A]](Magnolia.gen[A](c))
    c.universe.reify(new Exported(magnoliaTree.splice))
  }
}
