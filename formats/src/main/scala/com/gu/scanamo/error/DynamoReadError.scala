package org.scanamo.error

import cats.data.NonEmptyList
import cats.{Semigroup, Show}
import software.amazon.awssdk.core.util.{DefaultSdkAutoConstructList, DefaultSdkAutoConstructMap}
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, ConditionalCheckFailedException}
import scala.collection.JavaConverters._
sealed abstract class ScanamoError

final case class ConditionNotMet(e: ConditionalCheckFailedException) extends ScanamoError

sealed abstract class DynamoReadError extends ScanamoError

final case class NoPropertyOfType(propertyType: String, actual: AttributeValue) extends DynamoReadError {
  override def toString: String = {
    s"NoPropertyOfType($propertyType,${DynamoReadError.formatAttribute(actual)})"
  }
}

final case class TypeCoercionError(t: Throwable) extends DynamoReadError

final case object MissingProperty extends DynamoReadError

final case class PropertyReadError(name: String, problem: DynamoReadError)

final case class InvalidPropertiesError(errors: NonEmptyList[PropertyReadError]) extends DynamoReadError

object InvalidPropertiesError {

  import cats.syntax.semigroup._

  implicit object SemigroupInstance extends Semigroup[InvalidPropertiesError] {
    override def combine(x: InvalidPropertiesError, y: InvalidPropertiesError): InvalidPropertiesError =
      InvalidPropertiesError(x.errors |+| y.errors)
  }

}

object DynamoReadError {

  implicit object ShowInstance extends Show[DynamoReadError] {
    def show(e: DynamoReadError): String = describe(e)
  }

  def describe(d: DynamoReadError): String = d match {
    case InvalidPropertiesError(problems) =>
      problems.toList.map(p => s"'${p.name}': ${describe(p.problem)}").mkString(", ")
    case NoPropertyOfType(propertyType, actual) =>
      s"not of type: '$propertyType' was '${formatAttribute(actual)}'"
    case TypeCoercionError(e) => s"could not be converted to desired type: $e"
    case MissingProperty => "missing"
  }

  def formatAttribute(a: AttributeValue): String = {
    val out = List(
      Option(a.n()).map(i => "N" -> i),
      Option(a.s()).map(s => "S" -> s),
      Option(a.b()).map("B" -> _.asUtf8String()),
      Option(a.bool()).map("BOOL" -> _.booleanValue().toString),
      Option(a.bs()).find(!_.isInstanceOf[DefaultSdkAutoConstructList[_]]).map(bs => "BS" -> bs.asScala.map(_.asUtf8String()).toList.mkString("[", ", ", "]")),
      Option(a.l()).find(!_.isInstanceOf[DefaultSdkAutoConstructList[_]]).map(l => "L" -> l.asScala.map(formatAttribute).toList.mkString("[", ", ", "]")),
      Option(a.m()).find(!_.isInstanceOf[DefaultSdkAutoConstructMap[_, _]]).map(m => {
        "M" -> m.asScala.toMap.mapValues(formatAttribute).mkString("{", ", ", "}")
      }),
      Option(a.ss()).find(!_.isInstanceOf[DefaultSdkAutoConstructList[_]]).map(ss => "SS" -> ss.asScala.toList.mkString("[", ", ", "]")),
      Option(a.ns()).find(!_.isInstanceOf[DefaultSdkAutoConstructList[_]]).map(ns => "NS" -> ns.asScala.toList.mkString("[", ", ", "]")),
      Option(a.nul()).map("NULL" -> _.booleanValue().toString)
    ).flatten.map {
      case (id, value) => s"$id: $value"
    }
    out.mkString("{", ", ", "}")
  }
}
