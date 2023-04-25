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
