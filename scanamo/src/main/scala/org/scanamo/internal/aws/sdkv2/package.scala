package org.scanamo.internal.aws

import cats.implicits.*
import org.scanamo.internal.aws.sdkv2.HasCondition.HasConditionOps
import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.*
import org.scanamo.internal.aws.sdkv2.HasUpdateAndCondition.*
import org.scanamo.request.*
import org.scanamo.request.AWSSdkV2.{HasCondition, HasExpressionAttributes, HasUpdateAndCondition}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.utils.builder.SdkBuilder

import java.util
import scala.language.implicitConversions

package object sdkv2 {
  implicit def javaKeyFor(k: KeyedByKey): util.Map[String, AttributeValue] = k.key.toJavaMap
  implicit def javaKeyFor(k: KeyedByItem): util.Map[String, AttributeValue] = k.item.asObject.orEmpty.toJavaMap

  def baseSettings[T, B <: SdkBuilder[B, T]: HasExpressionAttributes](as: AttributesSummation)(
    builder: B
  ): T = builder.tableName(as.tableName).attributes(as.attributes).build()

  def baseWithOptCond[T, B <: SdkBuilder[B, T]: HasCondition](req: WithOptionalCondition)(
    builder: B
  ): T = baseSettings[T, B](req)(builder.setOptionalCondition(req))

  def baseWithUpdate[T, B <: SdkBuilder[B, T]: HasUpdateAndCondition](req: Updating)(builder: B): T =
    baseSettings[T, B](req)(builder.updateAndCondition(req.updateAndCondition))

  implicit class RichBuilder[B](builder: B) {
    def setOpt[V](opt: Option[V])(f: B => V => B): B = opt.foldLeft(builder) { (b, v) =>
      f(b)(v)
    }

    def set[V](v: V)(f: B => V => B): B = f(builder)(v)

    // def expression(c: RequestCondition)(f: B => String => B): B = f(builder)(c.expression)
  }
}
