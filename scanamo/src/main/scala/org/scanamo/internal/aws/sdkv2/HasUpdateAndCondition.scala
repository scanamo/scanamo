package org.scanamo.internal.aws.sdkv2

import org.scanamo.internal.aws.sdkv2.HasCondition.*
import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.*
// import org.scanamo.internal.aws.sdkv2.HasUpdateAndCondition.*
import org.scanamo.request.AttributeNamesAndValues
import org.scanamo.update.UpdateAndCondition
import software.amazon.awssdk.services.dynamodb.model
import software.amazon.awssdk.services.dynamodb.model.*

import java.util
import scala.collection.JavaConverters.*

object HasExpressionAttributes {

  // type AR[x <: ActionRequest[x]] = ActionRequest[x]

  // software.amazon.awssdk.services.dynamodb.model.DynamoDbRequest.Builder
  // software.amazon.awssdk.utils.builder.SdkBuilder: public interface SdkBuilder<B extends SdkBuilder<B, T>, T> extends Foo {

  // type Grrr[B] = software.amazon.awssdk.utils.builder.SdkBuilder[B, _]

  type Moo[t] = software.amazon.awssdk.utils.builder.SdkBuilder[_, t]

  type Boom[t, b <: software.amazon.awssdk.utils.builder.SdkBuilder[b, t]] =
    software.amazon.awssdk.utils.builder.SdkBuilder[b, t]

  type Boo[b <: software.amazon.awssdk.utils.builder.SdkBuilder[b, _]] =
    software.amazon.awssdk.utils.builder.SdkBuilder[b, _]

  type Foo = Boo[_]

  type BV[B, V] = B => V => B

  case class Has[B <: Foo](
    tableName: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasExpressionAttributes[B]

  implicit class HasExpressionAttributesOps[B <: Foo](val b: B)(implicit h: HasExpressionAttributes[B]) {
    b.build()

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

trait HasExpressionAttributes[B <: Foo] {
  type B2B[V] = BV[B, V]

  val tableName: B2B[String]
  val expressionAttributeNames: B2B[util.Map[String, String]]
  val expressionAttributeValues: B2B[util.Map[String, AttributeValue]]
}

trait HasCondition[B <: Foo] extends HasExpressionAttributes[B] {
  val conditionExpression: B2B[String]
}

object HasCondition {
  case class Has[T, B <: Boom[T, B]](
    tableName: BV[B, String],
    conditionExpression: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasCondition[B]

  implicit class HasConditionOps[B <: Foo](val b: B)(implicit h: HasCondition[B]) {
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
  case class Has[T, B <: Boom[T, B]](
    tableName: BV[B, String],
    updateExpression: BV[B, String],
    conditionExpression: BV[B, String],
    expressionAttributeNames: BV[B, util.Map[String, String]],
    expressionAttributeValues: BV[B, util.Map[String, AttributeValue]]
  ) extends HasUpdateAndCondition[B]

  implicit class Ops[B <: Foo](val b: B)(implicit h: HasUpdateAndCondition[B]) {
    def updateExpression(expression: String): B = h.updateExpression(b)(expression)

    def updateAndCondition(uac: UpdateAndCondition): B =
      b.updateExpression(uac.update.expression)
        .attributes(uac.attributes)
        .setOpt(uac.condition.map(_.expression))(_.conditionExpression)
  }

  implicit val uir: Has[UpdateItemRequest, UpdateItemRequest.Builder] =
    Has(_.tableName, _.updateExpression, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

  implicit val u: Has[Update, Update.Builder] =
    Has(_.tableName, _.updateExpression, _.conditionExpression, _.expressionAttributeNames, _.expressionAttributeValues)

}

trait HasUpdateAndCondition[B <: Foo] extends HasCondition[B] {
  val updateExpression: B2B[String]
}
