package org.scanamo.internal.aws.sdkv2

import cats.Endo
import org.scanamo.internal.aws.sdkv2.HasCondition.*
import org.scanamo.request.*
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.utils.builder.SdkBuilder

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

//  case class X[T](v: TransactWriteItem.Builder => T => TransactWriteItem.Builder)

  // call the correct method on TransactWriteItem.Builder - .put, .update, etc
  // make a Put/Update/etc builder
  // given a Put/Update/etc builder, populate it with 'standard' stuff for the item - ie every Putting gets this.
  // given a Put/Update/etc builder, populate it with custom stuff
  // type parameter just needs to be the TWA

  // TransactPutItem extends Putting - so can it take advantage of a type-class for Putting?

//  def grump[CR <: CRUD, T, B <: SdkBuilder[B, T]](
//    twiMethod: TransactWriteItem.Builder => T => TransactWriteItem.Builder, // make a Put/Update/etc builder
//    builderMaker: () => B, // make a builder
//    f: CR => Endo[B] // modify builder with custom stuff
//  ): CR => TransactWriteItem = cr => twiMethod(TransactWriteItem.builder())(f(cr)(builderMaker()).build()).build()
//
//  implicit val tcp: C[TransactPutItem] = grump[Putting, Put, Put.Builder](_.put, Put.builder, item => _.item(item))
//  implicit val tcd: C[TransactDeleteItem] = grump[Deleting, Delete, Delete.Builder](_.delete, Delete.builder, item => _.key(item))

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
