package org.scanamo.internal.aws.sdkv2

import cats.Endo
import org.scanamo.internal.aws.sdkv2.HasCondition.*
import org.scanamo.request.*
import software.amazon.awssdk.services.dynamodb.model.*

object TransactionItems {
  import Transformations.*
  implicit class RichTransactWriteAction(twa: TransactWriteAction) {
    def toAwsSdk: TransactWriteItem = twa match {
      case p: TransactPutItem        => knownToAwsSdk(p)
      case u: TransactUpdateItem     => knownToAwsSdk(u)
      case d: TransactDeleteItem     => knownToAwsSdk(d)
      case c: TransactConditionCheck => knownToAwsSdk(c)
    }
  }
}

private object Transformations {

  case class B[TWA <: TransactWriteAction](v: TWA => Endo[TransactWriteItem.Builder])

  implicit val tp: B[TransactPutItem] =
    B(item => _.put(baseWithOptCond[Put, Put.Builder](item)(Put.builder.item(item))))

  implicit val tu: B[TransactUpdateItem] =
    B(item => _.update(baseWithUpdate[Update, Update.Builder](item)(Update.builder.key(item))))

  implicit val td: B[TransactDeleteItem] =
    B(item => _.delete(baseWithOptCond[Delete, Delete.Builder](item)(Delete.builder.key(item))))

  implicit val tc: B[TransactConditionCheck] = B(item =>
    _.conditionCheck(
      baseSettings[ConditionCheck, ConditionCheck.Builder](item)(
        ConditionCheck.builder.key(item).conditionExpression(item.condition.expression)
      )
    )
  )

  def knownToAwsSdk[TWA <: TransactWriteAction](twa: TWA)(implicit b: B[TWA]): TransactWriteItem =
    b.v(twa)(TransactWriteItem.builder()).build()
}
