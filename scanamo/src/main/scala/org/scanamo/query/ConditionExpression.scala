package org.scanamo.query

import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.{DynamoFormat, DynamoObject}
import org.scanamo.error.{ConditionNotMet, ScanamoError}
import org.scanamo.ops.ScanamoOps
import org.scanamo.request.{ RequestCondition, ScanamoDeleteRequest, ScanamoPutRequest, ScanamoUpdateRequest }
import org.scanamo.update.UpdateExpression
import simulacrum.typeclass
import cats.syntax.either._

case class ConditionalOperation[V, T](tableName: String, t: T)(
  implicit state: ConditionExpression[T],
  format: DynamoFormat[V]
) {
  def put(item: V): ScanamoOps[Either[ConditionalCheckFailedException, PutItemResult]] =
    ScanamoOps.conditionalPut(
      ScanamoPutRequest(tableName, format.write(item), Some(state.apply(t)))
    )

  def delete(key: UniqueKey[_]): ScanamoOps[Either[ConditionalCheckFailedException, DeleteItemResult]] =
    ScanamoOps.conditionalDelete(
      ScanamoDeleteRequest(tableName = tableName, key = key.toDynamoObject, Some(state.apply(t)))
    )

  def update(key: UniqueKey[_], update: UpdateExpression): ScanamoOps[Either[ScanamoError, V]] =
    ScanamoOps
      .conditionalUpdate(
        ScanamoUpdateRequest(
          tableName,
          key.toDynamoObject,
          update.expression,
          update.attributeNames,
          DynamoObject(update.dynamoValues),
          update.addEmptyList,
          Some(state.apply(t))
        )
      )
      .map(
        either =>
          either
            .leftMap[ScanamoError](ConditionNotMet(_))
            .flatMap(r => format.read(DynamoObject(r.getAttributes).toDynamoValue))
      )
}

@typeclass trait ConditionExpression[T] {
  def apply(t: T): RequestCondition
}

object ConditionExpression {
  implicit def symbolValueEqualsCondition[V: DynamoFormat] = new ConditionExpression[(Symbol, V)] {
    override def apply(pair: (Symbol, V)): RequestCondition =
      attributeValueEqualsCondition.apply((AttributeName.of(pair._1), pair._2))
  }

  implicit def attributeValueEqualsCondition[V: DynamoFormat] = new ConditionExpression[(AttributeName, V)] {
    val prefix = "equalsCondition"
    override def apply(pair: (AttributeName, V)): RequestCondition = {
      val attributeName = pair._1
      RequestCondition(
        s"#${attributeName.placeholder(prefix)} = :conditionAttributeValue",
        attributeName.attributeNames(s"#$prefix"),
        Some(DynamoObject("conditionAttributeValue" -> pair._2))
      )
    }
  }

  implicit def symbolValueInCondition[V: DynamoFormat] = new ConditionExpression[(Symbol, Set[V])] {
    override def apply(pair: (Symbol, Set[V])): RequestCondition =
      attributeValueInCondition.apply((AttributeName.of(pair._1), pair._2))
  }

  implicit def attributeValueInCondition[V: DynamoFormat] =
    new ConditionExpression[(AttributeName, Set[V])] {
      val prefix = "inCondition"
      override def apply(pair: (AttributeName, Set[V])): RequestCondition = {
        val attributeName = pair._1
        val attributeValues = pair._2.zipWithIndex.foldLeft(DynamoObject.empty) {
          case (m, (v, i)) => m <> DynamoObject(s"conditionAttributeValue$i" -> v)
        }
        RequestCondition(
          s"""#${attributeName
            .placeholder(prefix)} IN ${attributeValues.mapKeys(':' + _).keys.mkString("(", ",", ")")}""",
          attributeName.attributeNames(s"#$prefix"),
          Some(attributeValues)
        )
      }
    }

  implicit def attributeExistsCondition = new ConditionExpression[AttributeExists] {
    val prefix = "attributeExists"
    override def apply(t: AttributeExists): RequestCondition =
      RequestCondition(s"attribute_exists(#${t.key.placeholder(prefix)})", t.key.attributeNames(s"#$prefix"), None)
  }

  implicit def attributeNotExistsCondition = new ConditionExpression[AttributeNotExists] {
    val prefix = "attributeNotExists"
    override def apply(t: AttributeNotExists): RequestCondition =
      RequestCondition(s"attribute_not_exists(#${t.key.placeholder(prefix)})", t.key.attributeNames(s"#$prefix"), None)
  }

  implicit def notCondition[T](implicit pcs: ConditionExpression[T]) = new ConditionExpression[Not[T]] {
    override def apply(not: Not[T]): RequestCondition = {
      val conditionToNegate = pcs(not.condition)
      conditionToNegate.copy(expression = s"NOT(${conditionToNegate.expression})")
    }
  }

  implicit def beginsWithCondition[V: DynamoFormat] = new ConditionExpression[BeginsWith[V]] {
    val prefix = "beginsWith"
    override def apply(b: BeginsWith[V]): RequestCondition =
      RequestCondition(
        s"begins_with(#${b.key.placeholder(prefix)}, :conditionAttributeValue)",
        b.key.attributeNames(s"#$prefix"),
        Some(DynamoObject("conditionAttributeValue" -> b.v))
      )
  }

  implicit def betweenCondition[V: DynamoFormat] = new ConditionExpression[Between[V]] {
    val prefix = "between"
    override def apply(b: Between[V]): RequestCondition =
      RequestCondition(
        s"#${b.key.placeholder(prefix)} BETWEEN :lower and :upper",
        b.key.attributeNames(s"#$prefix"),
        Some(
          DynamoObject(
            "lower" -> b.bounds.lowerBound.v,
            "upper" -> b.bounds.upperBound.v
          )
        )
      )
  }

  implicit def keyIsCondition[V: DynamoFormat] = new ConditionExpression[KeyIs[V]] {
    val prefix = "keyIs"
    override def apply(k: KeyIs[V]): RequestCondition =
      RequestCondition(
        s"#${k.key.placeholder(prefix)} ${k.operator.op} :conditionAttributeValue",
        k.key.attributeNames(s"#$prefix"),
        Some(DynamoObject("conditionAttributeValue" -> k.v))
      )
  }

  implicit def andCondition[L: ConditionExpression, R: ConditionExpression] =
    new ConditionExpression[AndCondition[L, R]] {
      override def apply(and: AndCondition[L, R]): RequestCondition =
        combineConditions(and.l, and.r, "AND")
    }

  implicit def orCondition[L: ConditionExpression, R: ConditionExpression] =
    new ConditionExpression[OrCondition[L, R]] {
      override def apply(and: OrCondition[L, R]): RequestCondition =
        combineConditions(and.l, and.r, "OR")
    }

  private def prefixKeys[T](map: Map[String, T], prefix: String, magicChar: Char): Map[String, T] = map.map {
    case (k, v) => (newKey(k, prefix, Some(magicChar)), v)
  }

  private def newKey(oldKey: String, prefix: String, magicChar: Option[Char]): String =
    magicChar.fold(s"$prefix$oldKey")(mc => s"$mc$prefix${oldKey.stripPrefix(mc.toString)}")

  private def prefixKeysIn(string: String, keys: Iterable[String], prefix: String, magicChar: Option[Char]): String =
    keys.foldLeft(string)((result, key) => result.replaceAllLiterally(key, newKey(key, prefix, magicChar)))

  private def combineConditions[L, R](l: L, r: R, combininingOperator: String)(
    implicit lce: ConditionExpression[L],
    rce: ConditionExpression[R]
  ): RequestCondition = {
    val lPrefix: String = s"${combininingOperator.toLowerCase}_l_"
    val rPrefix: String = s"${combininingOperator.toLowerCase}_r_"

    val lCondition: RequestCondition = lce(l)
    val rCondition: RequestCondition = rce(r)

    val mergedExpressionAttributeNames: Map[String, String] =
      prefixKeys(lCondition.attributeNames, lPrefix, '#') ++
        prefixKeys(rCondition.attributeNames, rPrefix, '#')

    val mergedExpressionAttributeValues =
      (lCondition.dynamoValues.map(_.mapKeys(lPrefix ++ _)) getOrElse DynamoObject.empty) <>
        (rCondition.dynamoValues.map(_.mapKeys(rPrefix ++ _)) getOrElse DynamoObject.empty)

    val lConditionExpression =
      prefixKeysIn(
        prefixKeysIn(lCondition.expression, lCondition.attributeNames.keys, lPrefix, Some('#')),
        lCondition.dynamoValues.toList.flatMap(_.keys),
        lPrefix,
        None
      )
    val rConditionExpression =
      prefixKeysIn(
        prefixKeysIn(rCondition.expression, rCondition.attributeNames.keys, rPrefix, Some('#')),
        rCondition.dynamoValues.toList.flatMap(_.keys),
        rPrefix,
        None
      )

    RequestCondition(
      s"($lConditionExpression $combininingOperator $rConditionExpression)",
      mergedExpressionAttributeNames,
      if (mergedExpressionAttributeValues.isEmpty) None else Some(mergedExpressionAttributeValues)
    )
  }
}

case class AndCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class OrCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class Condition[T: ConditionExpression](t: T) {
  def apply = implicitly[ConditionExpression[T]].apply(t)
  def and[Y: ConditionExpression](other: Y) = AndCondition(t, other)
  def or[Y: ConditionExpression](other: Y) = OrCondition(t, other)
}

object Condition {
  implicit def conditionExpression[T]: ConditionExpression[Condition[T]] =
    new ConditionExpression[Condition[T]] {
      override def apply(condition: Condition[T]): RequestCondition = condition.apply
    }
}
