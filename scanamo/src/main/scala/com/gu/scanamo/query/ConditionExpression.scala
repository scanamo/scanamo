package org.scanamo.query

import org.scanamo.DynamoFormat
import org.scanamo.error.{ConditionNotMet, ScanamoError}
import org.scanamo.ops.ScanamoOps
import org.scanamo.request.{RequestCondition, ScanamoDeleteRequest, ScanamoPutRequest, ScanamoUpdateRequest}
import org.scanamo.update.UpdateExpression
import simulacrum.typeclass
import cats.syntax.either._
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, ConditionalCheckFailedException, DeleteItemResponse, PutItemResponse}

case class ConditionalOperation[V, T](tableName: String, t: T)(
  implicit state: ConditionExpression[T],
  format: DynamoFormat[V]
) {
  def put(item: V): ScanamoOps[Either[ConditionalCheckFailedException, PutItemResponse]] = {
    val unconditionalRequest = ScanamoPutRequest(tableName, format.write(item), None)
    ScanamoOps.conditionalPut(
      unconditionalRequest.copy(condition = Some(state.apply(t)(unconditionalRequest.condition)))
    )
  }

  def delete(key: UniqueKey[_]): ScanamoOps[Either[ConditionalCheckFailedException, DeleteItemResponse]] = {
    val unconditionalRequest = ScanamoDeleteRequest(tableName = tableName, key = key.asAVMap, None)
    ScanamoOps.conditionalDelete(
      unconditionalRequest.copy(condition = Some(state.apply(t)(unconditionalRequest.condition)))
    )
  }

  def update(key: UniqueKey[_], update: UpdateExpression): ScanamoOps[Either[ScanamoError, V]] = {

    val unconditionalRequest = ScanamoUpdateRequest(
      tableName,
      key.asAVMap,
      update.expression,
      update.attributeNames,
      update.attributeValues,
      None
    )
    ScanamoOps
      .conditionalUpdate(unconditionalRequest.copy(condition = Some(state.apply(t)(unconditionalRequest.condition))))
      .map(
        either =>
          either
            .leftMap[ScanamoError](ConditionNotMet)
            .flatMap(r => format.read {
              AttributeValue.builder().m(r.attributes()).build()
            })
      )
  }
}

@typeclass trait ConditionExpression[T] {
  def apply(t: T)(condition: Option[RequestCondition]): RequestCondition
}

object ConditionExpression {
  implicit def symbolValueEqualsCondition[V: DynamoFormat] = new ConditionExpression[(Symbol, V)] {
    override def apply(pair: (Symbol, V))(condition: Option[RequestCondition]): RequestCondition =
      attributeValueEqualsCondition.apply((AttributeName.of(pair._1), pair._2))(condition)
  }

  implicit def attributeValueEqualsCondition[V: DynamoFormat] = new ConditionExpression[(AttributeName, V)] {
    val prefix = "equalsCondition"
    override def apply(pair: (AttributeName, V))(condition: Option[RequestCondition]): RequestCondition = {
      val attributeName = pair._1
      RequestCondition(
        s"#${attributeName.placeholder(prefix)} = :conditionAttributeValue",
        attributeName.attributeNames(s"#$prefix"),
        Some(Map(":conditionAttributeValue" -> DynamoFormat[V].write(pair._2)))
      )
    }
  }

  implicit def symbolValueInCondition[V: DynamoFormat] = new ConditionExpression[(Symbol, Set[V])] {
    override def apply(pair: (Symbol, Set[V]))(condition: Option[RequestCondition]): RequestCondition =
      attributeValueInCondition.apply((AttributeName.of(pair._1), pair._2))(condition)
  }

  implicit def attributeValueInCondition[V: DynamoFormat] = new ConditionExpression[(AttributeName, Set[V])] {
    val prefix = "inCondition"
    override def apply(pair: (AttributeName, Set[V]))(condition: Option[RequestCondition]): RequestCondition = {
      val format = DynamoFormat[V]
      val attributeName = pair._1
      val attributeValues = pair._2.zipWithIndex.map {
        case (v, i) =>
          s":conditionAttributeValue$i" -> format.write(v)
      }.toMap
      RequestCondition(
        s"""#${attributeName.placeholder(prefix)} IN ${attributeValues.keys.mkString("(", ",", ")")}""",
        attributeName.attributeNames(s"#$prefix"),
        Some(attributeValues)
      )
    }
  }

  implicit def attributeExistsCondition = new ConditionExpression[AttributeExists] {
    val prefix = "attributeExists"
    override def apply(t: AttributeExists)(condition: Option[RequestCondition]): RequestCondition =
      RequestCondition(s"attribute_exists(#${t.key.placeholder(prefix)})", t.key.attributeNames(s"#$prefix"), None)
  }

  implicit def attributeNotExistsCondition = new ConditionExpression[AttributeNotExists] {
    val prefix = "attributeNotExists"
    override def apply(t: AttributeNotExists)(condition: Option[RequestCondition]): RequestCondition =
      RequestCondition(s"attribute_not_exists(#${t.key.placeholder(prefix)})", t.key.attributeNames(s"#$prefix"), None)
  }

  implicit def notCondition[T](implicit pcs: ConditionExpression[T]) = new ConditionExpression[Not[T]] {
    override def apply(not: Not[T])(condition: Option[RequestCondition]): RequestCondition = {
      val conditionToNegate = pcs(not.condition)(condition)
      conditionToNegate.copy(expression = s"NOT(${conditionToNegate.expression})")
    }
  }

  implicit def beginsWithCondition[V: DynamoFormat] = new ConditionExpression[BeginsWith[V]] {
    val prefix = "beginsWith"
    override def apply(b: BeginsWith[V])(condition: Option[RequestCondition]): RequestCondition =
      RequestCondition(
        s"begins_with(#${b.key.placeholder(prefix)}, :conditionAttributeValue)",
        b.key.attributeNames(s"#$prefix"),
        Some(Map(":conditionAttributeValue" -> DynamoFormat[V].write(b.v)))
      )
  }

  implicit def betweenCondition[V: DynamoFormat] = new ConditionExpression[Between[V]] {
    val prefix = "between"
    override def apply(b: Between[V])(condition: Option[RequestCondition]): RequestCondition =
      RequestCondition(
        s"#${b.key.placeholder(prefix)} BETWEEN :lower and :upper",
        b.key.attributeNames(s"#$prefix"),
        Some(
          Map(
            ":lower" -> DynamoFormat[V].write(b.bounds.lowerBound.v),
            ":upper" -> DynamoFormat[V].write(b.bounds.upperBound.v)
          )
        )
      )
  }

  implicit def keyIsCondition[V: DynamoFormat] = new ConditionExpression[KeyIs[V]] {
    val prefix = "keyIs"
    override def apply(k: KeyIs[V])(condition: Option[RequestCondition]): RequestCondition =
      RequestCondition(
        s"#${k.key.placeholder(prefix)} ${k.operator.op} :conditionAttributeValue",
        k.key.attributeNames(s"#$prefix"),
        Some(Map(":conditionAttributeValue" -> DynamoFormat[V].write(k.v)))
      )
  }

  implicit def andCondition[L, R](implicit lce: ConditionExpression[L], rce: ConditionExpression[R]) =
    new ConditionExpression[AndCondition[L, R]] {
      override def apply(and: AndCondition[L, R])(condition: Option[RequestCondition]): RequestCondition =
        combineConditions(condition, and.l, and.r, "AND")
    }

  implicit def orCondition[L, R](implicit lce: ConditionExpression[L], rce: ConditionExpression[R]) =
    new ConditionExpression[OrCondition[L, R]] {
      override def apply(and: OrCondition[L, R])(condition: Option[RequestCondition]): RequestCondition =
        combineConditions(condition, and.l, and.r, "OR")
    }

  private def combineConditions[L, R](condition: Option[RequestCondition], l: L, r: R, combininingOperator: String)(
    implicit lce: ConditionExpression[L],
    rce: ConditionExpression[R]
  ): RequestCondition = {
    def prefixKeys[T](map: Map[String, T], prefix: String, magicChar: Char) = map.map {
      case (k, v) => (newKey(k, prefix, magicChar), v)
    }
    def newKey(oldKey: String, prefix: String, magicChar: Char) =
      s"$magicChar$prefix${oldKey.stripPrefix(magicChar.toString)}"

    def prefixKeysIn(string: String, keys: Iterable[String], prefix: String, magicChar: Char) =
      keys.foldLeft(string)((result, key) => result.replaceAllLiterally(key, newKey(key, prefix, magicChar)))

    val lPrefix = s"${combininingOperator.toLowerCase}_l_"
    val rPrefix = s"${combininingOperator.toLowerCase}_r_"

    val lCondition = lce(l)(condition)
    val rCondition = rce(r)(condition)

    val mergedExpressionAttributeNames =
      prefixKeys(lCondition.attributeNames, lPrefix, '#') ++
        prefixKeys(rCondition.attributeNames, rPrefix, '#')

    val lValues = lCondition.attributeValues.map(prefixKeys(_, lPrefix, ':'))
    val rValues = rCondition.attributeValues.map(prefixKeys(_, rPrefix, ':'))

    val mergedExpressionAttributeValues = lValues match {
      case Some(m) => Some(m ++ rValues.getOrElse(Map.empty))
      case _       => rValues
    }

    val lConditionExpression =
      prefixKeysIn(
        prefixKeysIn(lCondition.expression, lCondition.attributeNames.keys, lPrefix, '#'),
        lCondition.attributeValues.toList.flatMap(_.keys),
        lPrefix,
        ':'
      )
    val rConditionExpression =
      prefixKeysIn(
        prefixKeysIn(rCondition.expression, rCondition.attributeNames.keys, rPrefix, '#'),
        rCondition.attributeValues.toList.flatMap(_.keys),
        rPrefix,
        ':'
      )
    RequestCondition(
      s"($lConditionExpression $combininingOperator $rConditionExpression)",
      mergedExpressionAttributeNames,
      mergedExpressionAttributeValues
    )
  }
}

case class AndCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class OrCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class Condition[T: ConditionExpression](t: T) {
  def apply(condition: Option[RequestCondition]) = implicitly[ConditionExpression[T]].apply(t)(condition)
  def and[Y: ConditionExpression](other: Y) = AndCondition(t, other)
  def or[Y: ConditionExpression](other: Y) = OrCondition(t, other)
}

object Condition {
  implicit def conditionExpression[T]: ConditionExpression[Condition[T]] =
    new ConditionExpression[Condition[T]] {
      override def apply(condition: Condition[T])(possibleCondition: Option[RequestCondition]): RequestCondition =
        condition(possibleCondition)
    }
}
