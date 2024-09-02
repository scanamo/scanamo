package org.scanamo.internal.aws.sdkv2

import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.*
import org.scanamo.request.AttributeNamesAndValues
import org.scanamo.update.UpdateAndCondition
import software.amazon.awssdk.services.dynamodb.model
import software.amazon.awssdk.services.dynamodb.model.*

import java.util
import scala.collection.JavaConverters.*

object HasExpressionAttributes {

  type BV[B, V] = B => V => B

  case class Has[B](
    tableName: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasExpressionAttributes[B]

  implicit class HasExpressionAttributesOps[B](val b: B)(implicit
    h: HasExpressionAttributes[B]
  ) {
    def tableName(name: String): B = h.tableName(b)(name)
    def expressionAttributeNames(names: util.Map[String, String]): B =
      h.expressionAttributeNames(b)(names)
    def expressionAttributeValues(values: util.Map[String, AttributeValue]): B =
      h.expressionAttributeValues(b)(values)

    def attributes(attributes: AttributeNamesAndValues): B = if (attributes.isEmpty) b
    else
      b.expressionAttributeNames(attributes.names.asJava)
        .setOpt(attributes.values.toExpressionAttributeValues)(_.expressionAttributeValues)
  }

  implicit val qr: Has[QueryRequest.Builder] =
    Has(_.tableName, _.expressionAttributeNames, _.expressionAttributeValues)
  implicit val sr: Has[ScanRequest.Builder] =
    Has(_.tableName, _.expressionAttributeNames, _.expressionAttributeValues)
}

trait HasExpressionAttributes[B] {
  type B2B[V] = BV[B, V]

  val tableName: B2B[String]
  val expressionAttributeNames: B2B[util.Map[String, String]]
  val expressionAttributeValues: B2B[util.Map[String, AttributeValue]]
}

trait HasCondition[B] extends HasExpressionAttributes[B] {
  val conditionExpression: B2B[String]
}

object HasCondition {
  case class Has[B](
    tableName: BV[B, String],
    conditionExpression: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasCondition[B]

  implicit class HasConditionOps[B](val b: B)(implicit h: HasCondition[B]) {
    def conditionExpression(expression: String): B = h.conditionExpression(b)(expression)
  }

  implicit val pir: Has[PutItemRequest.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val p: Has[Put.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val cc: Has[model.ConditionCheck.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val dir: Has[DeleteItemRequest.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val d: Has[Delete.Builder] =
    Has(_.tableName, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)
}

object HasUpdateAndCondition {
  case class Has[B](
    tableName: BV[B, String],
    updateExpression: BV[B, String],
    conditionExpression: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasUpdateAndCondition[B]

  implicit class HasUpdateAndConditionOps[B](val b: B)(implicit h: HasUpdateAndCondition[B]) {
    def updateExpression(expression: String): B = h.updateExpression(b)(expression)

    def updateAndCondition(uac: UpdateAndCondition): B =
      updateExpression(uac.update.expression).setOpt(uac.condition.map(_.expression))(h.conditionExpression)
  }

  implicit val uir: Has[UpdateItemRequest.Builder] =
    Has(_.tableName, _.updateExpression, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val u: Has[Update.Builder] =
    Has(_.tableName, _.updateExpression, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)
}

trait HasUpdateAndCondition[B] extends HasCondition[B] {
  val updateExpression: B2B[String]
}
