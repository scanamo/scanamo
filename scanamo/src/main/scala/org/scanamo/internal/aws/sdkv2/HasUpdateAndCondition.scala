package org.scanamo.internal.aws.sdkv2

import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.*
import org.scanamo.request.AttributeNamesAndValues
import org.scanamo.update.UpdateAndCondition
import software.amazon.awssdk.services.dynamodb.model
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.utils.builder.SdkBuilder

import java.util
import scala.collection.JavaConverters.*

object HasExpressionAttributes {

  type BV[B, V] = B => V => B

  case class Has[T, B <: SdkBuilder[B, T]](
    tableName: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasExpressionAttributes[T, B]

  implicit class HasExpressionAttributesOps[T, B <: SdkBuilder[B, T]](val b: B)(implicit
    h: HasExpressionAttributes[T, B]
  ) {
    def tableName(name: String): B = h.tableName(b)(name)
    def expressionAttributeNames(names: util.Map[String, String]): B =
      h.expressionAttributeNames(b)(names)
    def expressionAttributeValues(values: util.Map[String, AttributeValue]): B =
      h.expressionAttributeValues(b)(values)

    def attributes(attributes: AttributeNamesAndValues): B = if (attributes.isEmpty) b
    else
      expressionAttributeNames(attributes.names.asJava)
        .setOpt(attributes.values.toExpressionAttributeValues)(h.expressionAttributeValues)
  }

  implicit val qr: Has[QueryRequest, QueryRequest.Builder] =
    Has(_.tableName, _.expressionAttributeNames, _.expressionAttributeValues)
  implicit val sr: Has[ScanRequest, ScanRequest.Builder] =
    Has(_.tableName, _.expressionAttributeNames, _.expressionAttributeValues)
}

trait HasExpressionAttributes[T, B <: SdkBuilder[B, T]] {
  type B2B[V] = BV[B, V]

  val tableName: B2B[String]
  val expressionAttributeNames: B2B[util.Map[String, String]]
  val expressionAttributeValues: B2B[util.Map[String, AttributeValue]]
}

trait HasCondition[T, B <: SdkBuilder[B, T]] extends HasExpressionAttributes[T, B] {
  val conditionExpression: B2B[String]
}

object HasCondition {
  case class Has[T, B <: SdkBuilder[B, T]](
    tableName: BV[B, String],
    conditionExpression: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasCondition[T, B]

  implicit class HasConditionOps[T, B <: SdkBuilder[B, T]](val b: B)(implicit h: HasCondition[T, B]) {
    def conditionExpression(expression: String): B = h.conditionExpression(b)(expression)
  }

  implicit val pir: Has[PutItemRequest, PutItemRequest.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val p: Has[Put, Put.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val cc: Has[model.ConditionCheck, model.ConditionCheck.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val dir: Has[DeleteItemRequest, DeleteItemRequest.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val d: Has[Delete, Delete.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)
}

object HasUpdateAndCondition {
  case class Has[T, B <: SdkBuilder[B, T]](
    tableName: BV[B, String],
    updateExpression: BV[B, String],
    conditionExpression: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasUpdateAndCondition[T, B]

  implicit class HasUpdateAndConditionOps[T, B <: SdkBuilder[B, T]](val b: B)(implicit h: HasUpdateAndCondition[T, B]) {
    def updateExpression(expression: String): B = h.updateExpression(b)(expression)

    def updateAndCondition(uac: UpdateAndCondition): B =
      updateExpression(uac.update.expression).setOpt(uac.condition.map(_.expression))(h.conditionExpression)
  }

  implicit val uir: Has[UpdateItemRequest, UpdateItemRequest.Builder] =
    Has(_.tableName, _.updateExpression, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val u: Has[Update, Update.Builder] =
    Has(_.tableName, _.updateExpression, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

}

trait HasUpdateAndCondition[T, B <: SdkBuilder[B, T]] extends HasCondition[T, B] {
  val updateExpression: B2B[String]
}
