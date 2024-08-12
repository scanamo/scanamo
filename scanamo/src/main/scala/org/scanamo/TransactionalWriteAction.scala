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
import org.scanamo.request.{
  RequestCondition,
  TransactConditionCheck,
  TransactDeleteItem,
  TransactPunk,
  TransactPutItem,
  TransactUpdateItem
}
import org.scanamo.update.UpdateExpression


/**
 * There are at least 2 intrinsic representations of the TransactItems in a DynamoDB transact request:
 *
 * 1. AWS API request format: Seq[TransactWriteItem] (note ordering is almost completely immaterial)
 *    - each TransactWriteItem has:
 *      - TableName
 *      - ItemKey (Map[String,AttributeValue])
 *      - update/put/check/delete characteristics, including conditionals
 * 2. DynamoDB transaction constraints model: `Map[TableName, Map[ItemKey, TransactAction]]`
 *    - TableName (the table name string)
 *    - ItemKey (Map[String,AttributeValue])
 *    - TransactAction - the update/put/check/delete characteristics, including conditionals
 *      - Note that 'Put' is problematic in this model, as actually the ItemKey is redundant in terms
 *        of the information sent in the request - the Put contains a full Item, which includes
 *        the field(s) of its ItemKey
 *
 * In Scanamo, there are many representations:
 *
 * We have 2 representations of the TransactWriteItem of *1. AWS API request format*:
 *
 * a. The org.scanamo.TransactionalWriteAction trait  (Put, Update, etc)
 *   - the implementations are currently incomplete, do not include the optional conditional
 *   - Put Item is a V (type parameter), and requires an implicit DynamoFormat[V] to convert to DynamoValue
 *   - Update/Delete/Check: key is a UniqueKey[_]
 * b. The org.scanamo.request.TransactPunk trait  (TransactPutItem, TransactUpdateItem, etc)
 *   - include the optional conditional
 *   - Put Item is a DynamoValue
 *   - Update/Delete/Check: key is a DynamoObject
 *
 * Additionally, we have:
 * * org.scanamo.request.ScanamoTransactWriteRequest is unfortunate, because it does not correspond to
 *   either of the intrinsic models - it contains 4 separate lists, Seq[TransactPutItem],
 *   Seq[TransactUpdateItem], etc. I would like to get rid of it
 *
 * * The org.scanamo.AlternativeTransacts.Trans trait models the TransactAction of *2. DynamoDB transaction constraints model*
 *   - contains *just* the update/put/check/delete characteristics, no TableName or ItemKey
 *   - do we want a Put implementation for this? Note that the key ItemKey and the Put Item must match,
 *     and the Map[ItemKey, TransactAction] can not enforce that.
 */

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

object AlternativeTransacts {
  trait TransAction {
    def toTransactPunk(tableName: String, key: UniqueKey[_]): TransactPunk
  }

  def put[K, V: DynamoFormat](item: V, condition: Option[RequestCondition] = None)(implicit kf: KeyFinder[K,V]): (K, TransPutItem[V]) =
    kf.keyForValue(item) -> TransPutItem(item, condition)


  case class TransPutItem[V](item: V, condition: Option[RequestCondition] = None)(implicit format: DynamoFormat[V])
      extends TransAction {
    val dynamoValue: DynamoValue = format.write(item)

    override def toTransactPunk(tableName: String, key: UniqueKey[_]): TransactPunk =
      TransactPutItem(tableName, dynamoValue, condition)
  }

  case class TransUpdateItem(updateExpression: UpdateExpression, condition: Option[RequestCondition] = None)
      extends TransAction {
    override def toTransactPunk(tableName: String, key: UniqueKey[_]): TransactUpdateItem =
      TransactUpdateItem(tableName, key.toDynamoObject, updateExpression, condition)

    def requiring[T](condition: T)(implicit ce: ConditionExpression[T]): TransUpdateItem = copy(
      condition = Some(ce(condition).runEmptyA.value)
    )
  }

  case class TransDeleteItem(condition: Option[RequestCondition] = None) extends TransAction {
    override def toTransactPunk(tableName: String, key: UniqueKey[_]): TransactPunk =
      TransactDeleteItem(tableName, key.toDynamoObject, condition)

    def requiring[T](condition: T)(implicit ce: ConditionExpression[T]): TransDeleteItem = copy(
      condition = Some(ce(condition).runEmptyA.value)
    )
  }
  case class TransConditionCheck(condition: RequestCondition) extends TransAction {
    override def toTransactPunk(tableName: String, key: UniqueKey[_]): TransactPunk =
      TransactConditionCheck(tableName, key.toDynamoObject, condition)
  }
}
