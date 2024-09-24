/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo.query

import cats.data.State
import cats.implicits.*
import org.scanamo.*
import org.scanamo.ops.ScanamoOps
import org.scanamo.ops.ScanamoOps.Results.*
import org.scanamo.request.*
import org.scanamo.update.{ UpdateAndCondition, UpdateExpression }
import software.amazon.awssdk.services.dynamodb.model.{ AttributeValue, DeleteItemResponse, PutItemResponse }

final case class ConditionalOperation[V, T](tableName: String, t: T)(implicit
  expr: ConditionExpression[T],
  format: DynamoFormat[V]
) {
  def put(item: V): ScanamoOps[Either[ScanamoError, Unit]] =
    nativePut(PutReturn.Nothing, item).map(_.leftMap(ConditionNotMet.apply).void)

  def putAndReturn(ret: PutReturn)(item: V): ScanamoOps[Option[Either[ScanamoError, V]]] =
    nativePut(ret, item).map(decodeReturnValue[PutItemResponse](_, _.attributes))

  private def nativePut(ret: PutReturn, item: V): ScanamoOps[Conditional[PutItemResponse]] =
    ScanamoOps.conditionalPut(
      ScanamoPutRequest(tableName, format.write(item).asObject.orEmpty, Some(expr(t).runEmptyA.value), ret)
    )

  def delete(key: UniqueKey[_]): ScanamoOps[Either[ScanamoError, Unit]] =
    nativeDelete(DeleteReturn.Nothing, key).map(_.leftMap(ConditionNotMet.apply).void)

  def deleteAndReturn(ret: DeleteReturn)(key: UniqueKey[_]): ScanamoOps[Option[Either[ScanamoError, V]]] =
    nativeDelete(ret, key).map(decodeReturnValue[DeleteItemResponse](_, _.attributes))

  private def nativeDelete(ret: DeleteReturn, key: UniqueKey[_]): ScanamoOps[Conditional[DeleteItemResponse]] =
    ScanamoOps
      .conditionalDelete(
        ScanamoDeleteRequest(
          tableName = tableName,
          key = key.toDynamoObject,
          Some(expr(t).runEmptyA.value),
          ret
        )
      )

  private def decodeReturnValue[A](
    either: Conditional[A],
    attrs: A => java.util.Map[String, AttributeValue]
  ): Option[Either[ScanamoError, V]] = {
    import cats.data.EitherT

    EitherT
      .fromEither[Option](either)
      .leftMap(ConditionNotMet.apply)
      .flatMap(DeleteItemResponse =>
        EitherT[Option, ScanamoError, V](
          Option(attrs(DeleteItemResponse))
            .filterNot(_.isEmpty)
            .map(DynamoObject(_).toDynamoValue)
            .map(format.read)
        )
      )
      .value
  }

  def update(key: UniqueKey[_], update: UpdateExpression): ScanamoOps[Either[ScanamoError, V]] =
    ScanamoOps
      .conditionalUpdate(
        ScanamoUpdateRequest(
          tableName,
          key.toDynamoObject,
          UpdateAndCondition(update, Some(expr(t).runEmptyA.value))
        )
      )
      .map(
        _.leftMap(ConditionNotMet.apply)
          .flatMap(r => format.read(DynamoObject(r.attributes).toDynamoValue))
      )
}

trait ConditionExpression[-T] { self =>
  def apply(x: T): State[Int, RequestCondition]

  def contramap[S](f: S => T): ConditionExpression[S] = (x: S) => self(f(x))
}

object ConditionExpression {
  def apply[T](implicit C: ConditionExpression[T]): ConditionExpression[T] = C

  @deprecated("Use `attr === value` syntax", "1.0")
  implicit def stringValueEqualsCondition[V: DynamoFormat]: ConditionExpression[(String, V)] =
    attributeValueEqualsCondition[V].contramap { case (attr, v) => AttributeName.of(attr) -> v }

  @deprecated("Use `attr === value` syntax", "1.0")
  implicit def attributeValueEqualsCondition[V: DynamoFormat]: ConditionExpression[(AttributeName, V)] =
    keyEqualsCondition.contramap { case (attr, v) => KeyEquals(attr, v) }

  implicit def keyEqualsCondition[V: DynamoFormat]: ConditionExpression[KeyEquals[V]] = (key: KeyEquals[V]) =>
    State.inspect { cpt =>
      val prefix = s"equalsCondition$cpt"
      val attributeName = key.key
      val namePlaceholder = attributeName.placeholder(prefix)
      val valuePlaceholder = s"conditionAttributeValue$cpt"
      RequestCondition(
        s"#$namePlaceholder = :$valuePlaceholder",
        AttributeNamesAndValues(attributeName.attributeNames(s"#$prefix"), DynamoObject(valuePlaceholder -> key.v))
      )
    }

  @deprecated("Use `attr in values` syntax", "1.0")
  implicit def stringValueInCondition[V: DynamoFormat]: ConditionExpression[(String, Set[V])] =
    attributeValueInCondition.contramap { case (attr, vs) => AttributeName.of(attr) -> vs }

  @deprecated("Use `attr in values` syntax", "1.0")
  implicit def attributeValueInCondition[V: DynamoFormat]: ConditionExpression[(AttributeName, Set[V])] =
    keyListCondition.contramap { case (attr, vs) => KeyList(attr, vs) }

  implicit def keyListCondition[V: DynamoFormat]: ConditionExpression[KeyList[V]] = (keys: KeyList[V]) =>
    State.inspect { cpt =>
      val prefix = s"inCondition$cpt"
      val attributeName = keys.key
      val namePlaceholder = attributeName.placeholder(prefix)
      val valuePlaceholder = s"conditionAttributeValue$cpt"
      val attributeValues = keys.values
        .foldLeft(DynamoObject.empty -> 0) { case ((m, i), v) =>
          (m <> DynamoObject(s"$valuePlaceholder$i" -> v)) -> (i + 1)
        }
        ._1
      RequestCondition(
        s"""#$namePlaceholder IN ${attributeValues.mapKeys(k => s":$k").keys.mkString("(", ",", ")")}""",
        AttributeNamesAndValues(attributeName.attributeNames(s"#$prefix"), attributeValues)
      )
    }

  implicit def attributeExistsCondition: ConditionExpression[AttributeExists] = (t: AttributeExists) =>
    State.inspect { cpt =>
      val prefix = s"attributeExists$cpt"
      RequestCondition(
        s"attribute_exists(#${t.key.placeholder(prefix)})",
        AttributeNamesAndValues(t.key.attributeNames(s"#$prefix"), DynamoObject.empty)
      )
    }

  implicit def attributeNotExistsCondition: ConditionExpression[AttributeNotExists] = (t: AttributeNotExists) =>
    State.inspect { cpt =>
      val prefix = s"attributeNotExists$cpt"
      RequestCondition(
        s"attribute_not_exists(#${t.key.placeholder(prefix)})",
        AttributeNamesAndValues(t.key.attributeNames(s"#$prefix"), DynamoObject.empty)
      )
    }

  implicit val containsCondition: ConditionExpression[Contains] = (t: Contains) =>
    State.inspect { cpt =>
      val prefix = s"contains$cpt"
      val valuePlaceholder = s"containsAttributeValue$cpt"
      RequestCondition(
        s"contains(#${t.key.placeholder(prefix)}, :$valuePlaceholder)",
        AttributeNamesAndValues(
          t.key.attributeNames(s"#$prefix"),
          DynamoObject(valuePlaceholder -> DynamoValue.fromString(t.value))
        )
      )
    }

  implicit def notCondition[T](implicit pcs: ConditionExpression[T]): ConditionExpression[Not[T]] = (not: Not[T]) =>
    pcs(not.condition).map { conditionToNegate =>
      conditionToNegate.copy(expression = s"NOT(${conditionToNegate.expression})")
    }

  implicit def beginsWithCondition[V: DynamoFormat]: ConditionExpression[BeginsWith[V]] = (b: BeginsWith[V]) =>
    State.inspect { cpt =>
      val prefix = s"beginsWith$cpt"
      val valuePlaceholder = s"conditionAttributeValue$cpt"
      RequestCondition(
        s"begins_with(#${b.key.placeholder(prefix)}, :$valuePlaceholder)",
        AttributeNamesAndValues(b.key.attributeNames(s"#$prefix"), DynamoObject(valuePlaceholder -> b.v))
      )
    }

  implicit def betweenCondition[V: DynamoFormat]: ConditionExpression[Between[V]] = (b: Between[V]) =>
    State.inspect { cpt =>
      val prefix = s"between$cpt"
      val lowerPh = s"lower$cpt"
      val upperPh = s"upper$cpt"
      RequestCondition(
        s"#${b.key.placeholder(prefix)} BETWEEN :$lowerPh and :$upperPh",
        AttributeNamesAndValues(b.key.attributeNames(s"#$prefix"), DynamoObject(lowerPh -> b.lo, upperPh -> b.hi))
      )
    }

  implicit def keyIsCondition[V: DynamoFormat]: ConditionExpression[KeyIs[V]] = (k: KeyIs[V]) =>
    State.inspect { cpt =>
      val prefix = s"keyIs$cpt"
      val valuePlaceholder = s"conditionAttributeValue$cpt"
      RequestCondition(
        s"#${k.key.placeholder(prefix)} ${k.operator.op} :$valuePlaceholder",
        AttributeNamesAndValues(k.key.attributeNames(s"#$prefix"), DynamoObject(valuePlaceholder -> k.v))
      )
    }

  implicit def andCondition[L: ConditionExpression, R: ConditionExpression]: ConditionExpression[AndCondition[L, R]] =
    (and: AndCondition[L, R]) => combineConditions(and.l, and.r, "AND")

  implicit def orCondition[L: ConditionExpression, R: ConditionExpression]: ConditionExpression[OrCondition[L, R]] =
    (or: OrCondition[L, R]) => combineConditions(or.l, or.r, "OR")

  private def combineConditions[L, R](l: L, r: R, combiningOperator: String)(implicit
    lce: ConditionExpression[L],
    rce: ConditionExpression[R]
  ): State[Int, RequestCondition] =
    for {
      l <- lce(l)
      _ <- State.modify[Int](_ + 1)
      r <- rce(r)
    } yield RequestCondition(
      s"(${l.expression} $combiningOperator ${r.expression})",
      l.attributes |+| r.attributes
    )
}

case class AndCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class OrCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class Condition[T](t: T)(implicit T: ConditionExpression[T]) {
  def apply: State[Int, RequestCondition] = T.apply(t)
  def and[Y: ConditionExpression](other: Y): AndCondition[T, Y] = AndCondition(t, other)
  def or[Y: ConditionExpression](other: Y): OrCondition[T, Y] = OrCondition(t, other)
}

object Condition {
  implicit def conditionExpression[T]: ConditionExpression[Condition[T]] =
    new ConditionExpression[Condition[T]] {
      override def apply(condition: Condition[T]): State[Int, RequestCondition] = condition.apply
    }
}
