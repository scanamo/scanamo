package org.scanamo.internal.aws.sdkv2

import cats.Endo
import org.scanamo.update.UpdateAndCondition
import software.amazon.awssdk.services.dynamodb.model.{ AttributeValue, Update, UpdateItemRequest }
import software.amazon.awssdk.utils.builder.Buildable

import java.util
import scala.collection.JavaConverters.*

object HasUpdateAndCondition {
  private type Has[B <: Buildable] = HasUpdateAndCondition[B]

  implicit class Ops[B <: Buildable](val b: B) extends AnyVal {
    private type H = Has[B]

    def updateExpression(expression: String)(implicit h: H): B = h.updateExpression(expression)(b)
    def conditionExpression(expression: String)(implicit h: H): B = h.conditionExpression(expression)(b)
    def expressionAttributeNames(names: util.Map[String, String])(implicit h: H): B =
      h.expressionAttributeNames(names)(b)
    def expressionAttributeValues(values: util.Map[String, AttributeValue])(implicit h: H): B =
      h.expressionAttributeValues(values)(b)

    def updateAndCondition(uac: UpdateAndCondition)(implicit h: H): B =
      b.updateExpression(uac.update.expression)
        .setOpt(uac.condition.map(_.expression))(_ conditionExpression _)
        .expressionAttributeNames(uac.attributes.names.asJava)
        .setOpt(uac.attributes.values.toExpressionAttributeValues)(_ expressionAttributeValues _)

  }

  implicit val uir: Has[UpdateItemRequest.Builder] = new Has[UpdateItemRequest.Builder] {
    def updateExpression(expression: String): B2B = _.updateExpression(expression)
    def conditionExpression(expression: String): B2B = _.conditionExpression(expression)
    def expressionAttributeNames(names: util.Map[String, String]): B2B = _.expressionAttributeNames(names)
    def expressionAttributeValues(values: util.Map[String, AttributeValue]): B2B = _.expressionAttributeValues(values)
  }

  implicit val u: Has[Update.Builder] = new Has[Update.Builder] {
    def updateExpression(expression: String): B2B = _.updateExpression(expression)
    def conditionExpression(expression: String): B2B = _.conditionExpression(expression)
    def expressionAttributeNames(names: util.Map[String, String]): B2B = _.expressionAttributeNames(names)
    def expressionAttributeValues(values: util.Map[String, AttributeValue]): B2B = _.expressionAttributeValues(values)
  }
}

trait HasUpdateAndCondition[B <: Buildable] {
  protected type B2B = Endo[B]

  def updateExpression(expression: String): B2B
  def conditionExpression(expression: String): B2B
  def expressionAttributeNames(names: util.Map[String, String]): B2B
  def expressionAttributeValues(values: util.Map[String, AttributeValue]): B2B
}
