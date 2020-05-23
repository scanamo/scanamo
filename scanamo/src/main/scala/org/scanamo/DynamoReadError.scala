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

import cats.data.NonEmptyList
import cats.Show
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException

sealed abstract class ScanamoError
final case class ConditionNotMet(e: ConditionalCheckFailedException) extends ScanamoError

sealed abstract class DynamoReadError extends ScanamoError
final case class NoPropertyOfType(propertyType: String, actual: DynamoValue) extends DynamoReadError
final case class TypeCoercionError(t: Throwable) extends DynamoReadError
final case object MissingProperty extends DynamoReadError
final case class InvalidPropertiesError(errors: NonEmptyList[(String, DynamoReadError)]) extends DynamoReadError

object DynamoReadError {
  implicit object ShowInstance extends Show[DynamoReadError] {
    def show(e: DynamoReadError): String = describe(e)
  }

  def describe(d: DynamoReadError): String =
    d match {
      case InvalidPropertiesError(problems) =>
        problems.toList.map(p => s"'${p._1}': ${describe(p._2)}").mkString(", ")
      case NoPropertyOfType(propertyType, actual) => s"not of type: '$propertyType' was '$actual'"
      case TypeCoercionError(e)                   => s"could not be converted to desired type: $e"
      case MissingProperty                        => "missing"
    }
}
