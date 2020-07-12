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
import cats.instances.int._

import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  ConditionalCheckFailedException,
  DeleteItemResponse,
  PutItemResponse
}
import org.scanamo.{ ConditionNotMet, DeleteReturn, DynamoFormat, DynamoObject, PutReturn, ScanamoError }
import org.scanamo.ops.ScanamoOps
import org.scanamo.request.{ RequestCondition, ScanamoDeleteRequest, ScanamoPutRequest, ScanamoUpdateRequest }
import org.scanamo.update.UpdateExpression
import cats.instances.either._
import cats.instances.option._
import cats.syntax.either._
import cats.syntax.functor._

final case class ConditionalOperation[V, T](tableName: String, t: T)(implicit
  expr: ConditionExpression[T],
  format: DynamoFormat[V]
) {
  def put(item: V): ScanamoOps[Either[ScanamoError, Unit]] =
    nativePut(PutReturn.Nothing, item).map(_.leftMap(ConditionNotMet(_)).void)

  def putAndReturn(ret: PutReturn)(item: V): ScanamoOps[Option[Either[ScanamoError, V]]] =
    nativePut(ret, item).map(decodeReturnValue[PutItemResponse](_, _.attributes))

  private def nativePut(ret: PutReturn, item: V): ScanamoOps[Either[ConditionalCheckFailedException, PutItemResponse]] =
    ScanamoOps.conditionalPut(
      ScanamoPutRequest(tableName, format.write(item), Some(expr(t).runEmptyA.value), ret)
    )

  def delete(key: UniqueKey[_]): ScanamoOps[Either[ScanamoError, Unit]] =
    nativeDelete(DeleteReturn.Nothing, key).map(_.leftMap(ConditionNotMet(_)).void)

  def deleteAndReturn(ret: DeleteReturn)(key: UniqueKey[_]): ScanamoOps[Option[Either[ScanamoError, V]]] =
    nativeDelete(ret, key).map(decodeReturnValue[DeleteItemResponse](_, _.attributes))

  private def nativeDelete(ret: DeleteReturn,
                           key: UniqueKey[_]
  ): ScanamoOps[Either[ConditionalCheckFailedException, DeleteItemResponse]] =
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
    either: Either[ConditionalCheckFailedException, A],
    attrs: A => java.util.Map[String, AttributeValue]
  ): Option[Either[ScanamoError, V]] = {
    import cats.data.EitherT

    EitherT
      .fromEither[Option](either)
      .leftMap(ConditionNotMet(_))
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
          update.expression,
          update.attributeNames,
          DynamoObject(update.dynamoValues),
          update.addEmptyList,
          Some(expr(t).runEmptyA.value)
        )
      )
      .map(
        _.leftMap(ConditionNotMet(_))
          .flatMap(r => format.read(DynamoObject(r.attributes).toDynamoValue))
      )
}

trait ConditionExpression[-T] { self =>
  def apply(x: T): State[Int, RequestCondition]

  def contramap[S](f: S => T): ConditionExpression[S] =
    new ConditionExpression[S] {
      def apply(x: S): State[Int, RequestCondition] = self(f(x))
    }

}

object ConditionExpression {
  def apply[T](implicit C: ConditionExpression[T]): ConditionExpression[T] = C

  implicit def stringValueEqualsCondition[V: DynamoFormat]: ConditionExpression[(String, V)] =
    attributeValueEqualsCondition[V].contramap { case (attr, v) => AttributeName.of(attr) -> v }

  implicit def attributeValueEqualsCondition[V: DynamoFormat] =
    new ConditionExpression[(AttributeName, V)] {
      override def apply(pair: (AttributeName, V)): State[Int, RequestCondition] =
        State.inspect { cpt =>
          val prefix = s"equalsCondition$cpt"
          val attributeName = pair._1
          val namePlaceholder = attributeName.placeholder(prefix)
          val valuePlaceholder = s"conditionAttributeValue$cpt"
          RequestCondition(
            s"#$namePlaceholder = :$valuePlaceholder",
            attributeName.attributeNames(s"#$prefix"),
            Some(DynamoObject(valuePlaceholder -> pair._2))
          )
        }
    }

  implicit def stringValueInCondition[V: DynamoFormat]: ConditionExpression[(String, Set[V])] =
    attributeValueInCondition.contramap { case (attr, vs) => AttributeName.of(attr) -> vs }

  implicit def attributeValueInCondition[V: DynamoFormat]: ConditionExpression[(AttributeName, Set[V])] =
    new ConditionExpression[(AttributeName, Set[V])] {
      override def apply(pair: (AttributeName, Set[V])): State[Int, RequestCondition] =
        State.inspect { cpt =>
          val prefix = s"inCondition$cpt"
          val attributeName = pair._1
          val namePlaceholder = attributeName.placeholder(prefix)
          val valuePlaceholder = s"conditionAttributeValue$cpt"
          val attributeValues = pair._2
            .foldLeft(DynamoObject.empty -> 0) {
              case ((m, i), v) => (m <> DynamoObject(s"$valuePlaceholder$i" -> v)) -> (i + 1)
            }
            ._1
          RequestCondition(
            s"""#$namePlaceholder IN ${attributeValues.mapKeys(':' + _).keys.mkString("(", ",", ")")}""",
            attributeName.attributeNames(s"#$prefix"),
            Some(attributeValues)
          )
        }
    }

  implicit def attributeExistsCondition: ConditionExpression[AttributeExists] =
    new ConditionExpression[AttributeExists] {
      override def apply(t: AttributeExists): State[Int, RequestCondition] =
        State.inspect { cpt =>
          val prefix = s"attributeExists$cpt"
          RequestCondition(s"attribute_exists(#${t.key.placeholder(prefix)})", t.key.attributeNames(s"#$prefix"), None)
        }
    }

  implicit def attributeNotExistsCondition: ConditionExpression[AttributeNotExists] =
    new ConditionExpression[AttributeNotExists] {
      override def apply(t: AttributeNotExists): State[Int, RequestCondition] =
        State.inspect { cpt =>
          val prefix = s"attributeNotExists$cpt"
          RequestCondition(
            s"attribute_not_exists(#${t.key.placeholder(prefix)})",
            t.key.attributeNames(s"#$prefix"),
            None
          )
        }
    }

  implicit def notCondition[T](implicit pcs: ConditionExpression[T]): ConditionExpression[Not[T]] =
    new ConditionExpression[Not[T]] {
      override def apply(not: Not[T]): State[Int, RequestCondition] =
        pcs(not.condition).map { conditionToNegate =>
          conditionToNegate.copy(expression = s"NOT(${conditionToNegate.expression})")
        }
    }

  implicit def beginsWithCondition[V: DynamoFormat]: ConditionExpression[BeginsWith[V]] =
    new ConditionExpression[BeginsWith[V]] {
      override def apply(b: BeginsWith[V]): State[Int, RequestCondition] =
        State.inspect { cpt =>
          val prefix = s"beginsWith$cpt"
          val valuePlaceholder = s"conditionAttributeValue$cpt"
          RequestCondition(
            s"begins_with(#${b.key.placeholder(prefix)}, :$valuePlaceholder)",
            b.key.attributeNames(s"#$prefix"),
            Some(DynamoObject(valuePlaceholder -> b.v))
          )
        }
    }

  implicit def betweenCondition[V: DynamoFormat]: ConditionExpression[Between[V]] =
    new ConditionExpression[Between[V]] {

      override def apply(b: Between[V]): State[Int, RequestCondition] =
        State.inspect { cpt =>
          val prefix = s"between$cpt"
          val lowerPh = s"lower$cpt"
          val upperPh = s"upper$cpt"
          RequestCondition(
            s"#${b.key.placeholder(prefix)} BETWEEN :$lowerPh and :$upperPh",
            b.key.attributeNames(s"#$prefix"),
            Some(
              DynamoObject(
                lowerPh -> b.bounds.lowerBound,
                upperPh -> b.bounds.upperBound
              )
            )
          )
        }
    }

  implicit def keyIsCondition[V: DynamoFormat]: ConditionExpression[KeyIs[V]] =
    new ConditionExpression[KeyIs[V]] {
      override def apply(k: KeyIs[V]): State[Int, RequestCondition] =
        State.inspect { cpt =>
          val prefix = s"keyIs$cpt"
          val valuePlaceholder = s"conditionAttributeValue$cpt"
          RequestCondition(
            s"#${k.key.placeholder(prefix)} ${k.operator.op} :$valuePlaceholder",
            k.key.attributeNames(s"#$prefix"),
            Some(DynamoObject(valuePlaceholder -> k.v))
          )
        }
    }

  implicit def andCondition[L: ConditionExpression, R: ConditionExpression] =
    new ConditionExpression[AndCondition[L, R]] {
      override def apply(and: AndCondition[L, R]): State[Int, RequestCondition] =
        combineConditions(and.l, and.r, "AND")
    }

  implicit def orCondition[L: ConditionExpression, R: ConditionExpression] =
    new ConditionExpression[OrCondition[L, R]] {
      override def apply(and: OrCondition[L, R]): State[Int, RequestCondition] =
        combineConditions(and.l, and.r, "OR")
    }

  private def combineConditions[L, R](l: L, r: R, combininingOperator: String)(implicit
    lce: ConditionExpression[L],
    rce: ConditionExpression[R]
  ): State[Int, RequestCondition] =
    for {
      l <- lce(l)
      _ <- State.modify[Int](_ + 1)
      r <- rce(r)
    } yield RequestCondition(
      s"(${l.expression} $combininingOperator ${r.expression})",
      l.attributeNames ++ r.attributeNames,
      l.dynamoValues.flatMap(xs => r.dynamoValues.map(xs <> _)) orElse l.dynamoValues orElse r.dynamoValues
    )
}

case class AndCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class OrCondition[L: ConditionExpression, R: ConditionExpression](l: L, r: R)

case class Condition[T](t: T)(implicit T: ConditionExpression[T]) {
  def apply: State[Int, RequestCondition] = T.apply(t)
  def and[Y: ConditionExpression](other: Y) = AndCondition(t, other)
  def or[Y: ConditionExpression](other: Y) = OrCondition(t, other)
}

object Condition {
  implicit def conditionExpression[T]: ConditionExpression[Condition[T]] =
    new ConditionExpression[Condition[T]] {
      override def apply(condition: Condition[T]): State[Int, RequestCondition] = condition.apply
    }
}
