package org.scanamo.internal.aws.sdkv2

import org.scanamo.internal.aws.sdkv2.BuilderStuff.BV
import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.*
import org.scanamo.request.{AttributeNamesAndValues, WithOptionalCondition}
import org.scanamo.update.UpdateAndCondition
import software.amazon.awssdk.services.dynamodb.model
import software.amazon.awssdk.services.dynamodb.model.*

import scala.collection.JavaConverters.*
import org.scanamo.request.AWSSdkV2.*

object BuilderStuff {
  type BV[B, V] = B => V => B
}


object HasKey {
  case class Has[B](key: BV[B, AValueMap]) extends HasKey[B]



  implicit val pir: Has[DeleteItemRequest.Builder] = Has(_.key)
  implicit val uir: Has[UpdateItemRequest.Builder] = Has(_.key)
}


object HasItem {
  case class Has[B](item: BV[B, AValueMap]) extends HasItem[B]

  implicit class HasItemOps[B](val b: B)(implicit h: HasItem[B]) {
    def item(item: AValueMap): B = h.item(b)(item)
  }

  implicit val pir: Has[PutItemRequest.Builder] = Has(_.item)
}

object HasExpressionAttributes {
  case class Has[B](
    tableName: BV[B, String],
    expressionAttributeNames: BV[B, ANameMap],
    expressionAttributeValues: BV[B, AValueMap]
  ) extends HasExpressionAttributes[B]

  implicit class HasExpressionAttributesOps[B](val b: B)(implicit
    h: HasExpressionAttributes[B]
  ) {
    def tableName(name: String): B = h.tableName(b)(name)
    def expressionAttributeNames(names: ANameMap): B = h.expressionAttributeNames(b)(names)
    def expressionAttributeValues(values: AValueMap): B = h.expressionAttributeValues(b)(values)

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



object HasCondition {
  case class Has[B](
    tableName: BV[B, String],
    conditionExpression: BV[B, String],
    expressionAttributeNames: BV[B, ANameMap],
    expressionAttributeValues: BV[B, AValueMap]
  ) extends HasCondition[B]

  implicit class HasConditionOps[B](val b: B)(implicit h: HasCondition[B]) {
    def conditionExpression(expression: String): B = h.conditionExpression(b)(expression)

    def setOptionalCondition(r: WithOptionalCondition): B =
      b.setOpt(r.condition.map(_.expression))(_.conditionExpression)
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
    expressionAttributeNames: BV[B, ANameMap],
    expressionAttributeValues: BV[B, AValueMap]
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

