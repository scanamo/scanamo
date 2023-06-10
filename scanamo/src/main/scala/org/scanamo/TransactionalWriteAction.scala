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

import org.scanamo.query.{ ConditionExpression, UniqueKey }
import org.scanamo.update.UpdateExpression

sealed trait TransactionalWriteAction

object TransactionalWriteAction {
  case class Put[V](tableName: String, item: V)(implicit format: DynamoFormat[V]) extends TransactionalWriteAction {
    def asDynamoValue = format.write(item)
  }

  case class Update(tableName: String, key: UniqueKey[_], expression: UpdateExpression) extends TransactionalWriteAction

  case class Delete(tableName: String, key: UniqueKey[_]) extends TransactionalWriteAction

  case class ConditionCheck[T](tableName: String, key: UniqueKey[_], condition: T)(implicit
    conditionExpression: ConditionExpression[T]
  ) extends TransactionalWriteAction {
    def asRequestCondition = conditionExpression.apply(condition).runEmptyA.value
  }

}
