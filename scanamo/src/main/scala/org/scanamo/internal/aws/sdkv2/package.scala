package org.scanamo.internal.aws

import cats.implicits.*
import org.scanamo.internal.aws.sdkv2.HasCondition.HasConditionOps
import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.HasExpressionAttributesOps
import org.scanamo.internal.aws.sdkv2.HasUpdateAndCondition.HasUpdateAndConditionOps
import org.scanamo.request.*
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.utils.builder.SdkBuilder
import scala.language.implicitConversions

import java.util

package object sdkv2 {
  implicit def javaKeyFor(k: KeyedByKey): util.Map[String, AttributeValue] = k.key.toJavaMap
  implicit def javaKeyFor(k: KeyedByItem): util.Map[String, AttributeValue] = k.item.asObject.orEmpty.toJavaMap

  case class Dresser[C <: CRUD]() {
  }

  implicit val deleteDresser: Dresser[Deleting] = Dresser()

  def baseSettings[T, B <: SdkBuilder[B, T]](as: AttributesSummation)(
    builder: B
  )(implicit h: HasExpressionAttributes[T, B]): T =
    new HasExpressionAttributesOps[T, B](builder.set(as.tableName)(h.tableName)).attributes(as.attributes).build()

  def baseWithOptCond[T, B <: SdkBuilder[B, T]](req: WithOptionalCondition)(
    builder: B
  )(implicit h: HasCondition[T, B]): T =
    baseSettings[T, B](req)(
      builder.setOpt(req.condition)(b => cond => new HasConditionOps[T, B](b).conditionExpression(cond.expression))
    )

  def baseWithUpdate[T, B <: SdkBuilder[B, T]](req: Updating)(
    builder: B
  )(implicit h: HasUpdateAndCondition[T, B]): T =
    baseSettings[T, B](req)(new HasUpdateAndConditionOps[T, B](builder).updateAndCondition(req.updateAndCondition))

  implicit class RichBuilder[B](builder: B) {
    def setOpt[V](opt: Option[V])(f: B => V => B): B = opt.foldLeft(builder) { (b, v) =>
      f(b)(v)
    }

    def set[V](v: V)(f: B => V => B): B = f(builder)(v)

    def expression(c: RequestCondition)(f: B => String => B): B = f(builder)(c.expression)
  }
}
