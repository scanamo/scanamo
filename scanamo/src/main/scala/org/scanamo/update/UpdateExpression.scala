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

package org.scanamo.update

import cats.data.NonEmptyVector
import org.scanamo.query.*
import org.scanamo.request.AttributeNamesAndValues
import org.scanamo.update.LeafUpdateExpression.EmptyListName
import org.scanamo.update.UpdateExpression.{ prefixKeys, someEmptyList }
import org.scanamo.{ DynamoArray, DynamoFormat, DynamoObject, DynamoValue }
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import scala.collection.immutable.HashMap

sealed trait UpdateExpression extends Product with Serializable { self =>
  final def expression: String =
    typeExpressions.map { case (t, e) =>
      s"${t.op} ${e.map(_.expression).toVector.mkString(", ")}"
    }.mkString(" ")

  final def typeExpressions: HashMap[UpdateType, NonEmptyVector[LeafUpdateExpression]] =
    self match {
      case SimpleUpdate(leaf) => HashMap(leaf.updateType -> NonEmptyVector.of(leaf))
      case AndUpdate(l, r) =>
        val leftUpdates = l.typeExpressions.map { case (k, v) => k -> v.map(_.prefixKeys("l_")) }
        val rightUpdates = r.typeExpressions.map { case (k, v) => k -> v.map(_.prefixKeys("r_")) }

        leftUpdates.merged(rightUpdates) { case ((k, v1), (_, v2)) =>
          k -> (v1 concatNev v2)
        }
    }

  @deprecated("Use `attributes.names` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  final def attributeNames: Map[String, String] = attributes.names

  final def unprefixedAttributeNames: Map[String, String] = self match {
    case SimpleUpdate(leaf) => leaf.attributeNames
    case AndUpdate(l, r)    => l.unprefixedAttributeNames ++ r.unprefixedAttributeNames
  }

  @deprecated("Use `attributes` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  final def dynamoValues: Map[String, DynamoValue] = unprefixedDynamoValues

  final def attributes: AttributeNamesAndValues = AttributeNamesAndValues(
    names = unprefixedAttributeNames.map { case (k, v) => (s"#$k", v) },
    values = DynamoObject(unprefixedDynamoValues ++ (if (addEmptyList) someEmptyList else None))
  )

  final val addEmptyList: Boolean = self match {
    case SimpleUpdate(leaf) => leaf.addEmptyList
    case AndUpdate(l, r)    => l.addEmptyList || r.addEmptyList
  }

  @deprecated("Use `attributes` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  final def attributeValues: Map[String, AttributeValue] = unprefixedDynamoValues.mapValues(_.toAttributeValue).toMap

  final def unprefixedDynamoValues: Map[String, DynamoValue] = self match {
    case SimpleUpdate(leaf) => leaf.dynamoValue.toMap
    case AndUpdate(l, r) =>
      prefixKeys(l.unprefixedDynamoValues, "l_") ++ prefixKeys(r.unprefixedDynamoValues, "r_")
  }

  final def unprefixedAttributeValues: Map[String, AttributeValue] =
    unprefixedDynamoValues.mapValues(_.toAttributeValue).toMap

  final def and(that: UpdateExpression): UpdateExpression = AndUpdate(self, that)
}

final private[scanamo] case class SimpleUpdate(leaf: LeafUpdateExpression) extends UpdateExpression
final private[scanamo] case class AndUpdate(l: UpdateExpression, r: UpdateExpression) extends UpdateExpression

object UpdateExpression {
  private val someEmptyList: Some[(String, DynamoValue)] = Some(EmptyListName -> DynamoArray.empty.toDynamoValue)

  def prefixKeys[T](map: Map[String, T], prefix: String) =
    map.map { case (k, v) => (s"$prefix$k", v) }

  def setFromAttribute(from: AttributeName, to: AttributeName): UpdateExpression =
    SetExpression.fromAttribute(from, to)

  def remove(field: AttributeName): UpdateExpression = RemoveExpression(field)

  def setIfNotExists[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression =
    SetIfNotExistsExpression(attr, value)

  @deprecated("use uncurried `set(attr, value)` syntax", "1.0")
  def set[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    SetExpression(fieldValue._1, fieldValue._2)
  def set[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression =
    SetExpression(attr, value)

  @deprecated("use uncurried `append(attr, value)` syntax", "1.0")
  def append[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    AppendExpression(fieldValue._1, fieldValue._2)
  def append[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression =
    AppendExpression(attr, value)

  @deprecated("use uncurried `prepend(attr, value)` syntax", "1.0")
  def prepend[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    PrependExpression(fieldValue._1, fieldValue._2)
  def prepend[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression =
    PrependExpression(attr, value)

  @deprecated("use uncurried `appendAll(attr, value)` syntax", "1.0")
  def appendAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
    AppendAllExpression(fieldValue._1, fieldValue._2)
  def appendAll[V: DynamoFormat](attr: AttributeName, value: List[V]): UpdateExpression =
    AppendAllExpression(attr, value)

  @deprecated("use uncurried `prependAll(attr, value)` syntax", "1.0")
  def prependAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
    PrependAllExpression(fieldValue._1, fieldValue._2)
  def prependAll[V: DynamoFormat](attr: AttributeName, value: List[V]): UpdateExpression =
    PrependAllExpression(attr, value)

  @deprecated("use uncurried `add(attr, value)` syntax", "1.0")
  def add[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    AddExpression(fieldValue._1, fieldValue._2)
  def add[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression =
    AddExpression(attr, value)

  @deprecated("use uncurried `delete(attr, value)` syntax", "1.0")
  def delete[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    DeleteExpression(fieldValue._1, fieldValue._2)
  def delete[V: DynamoFormat](attr: AttributeName, value: V): UpdateExpression =
    DeleteExpression(attr, value)
}

sealed private[update] trait UpdateType { val op: String }
private[update] case object SET extends UpdateType { override val op = "SET" }
private[update] case object ADD extends UpdateType { override val op = "ADD" }
private[update] case object DELETE extends UpdateType { override val op = "DELETE" }
private[update] case object REMOVE extends UpdateType { override val op = "REMOVE" }

sealed private[update] trait LeafUpdateExpression { self =>
  final val updateType: UpdateType = self match {
    case _: LeafAddExpression            => ADD
    case _: LeafAppendExpression         => SET
    case _: LeafDeleteExpression         => DELETE
    case _: LeafPrependExpression        => SET
    case _: LeafRemoveExpression         => REMOVE
    case _: LeafSetExpression            => SET
    case _: LeafAttributeExpression      => SET
    case _: LeafSetIfNotExistsExpression => SET
  }

  final val addEmptyList: Boolean = self match {
    case _: LeafAppendExpression | _: LeafPrependExpression => true
    case _                                                  => false
  }

  final val expression: String = self match {
    case LeafAddExpression(namePlaceholder, _, valuePlaceholder, _)    => s"#$namePlaceholder :$valuePlaceholder"
    case LeafDeleteExpression(namePlaceholder, _, valuePlaceholder, _) => s"#$namePlaceholder :$valuePlaceholder"
    case LeafSetExpression(namePlaceholder, _, valuePlaceholder, _)    => s"#$namePlaceholder = :$valuePlaceholder"
    case LeafAppendExpression(namePlaceholder, _, valuePlaceholder, _) =>
      s"#$namePlaceholder = list_append(if_not_exists(#$namePlaceholder, :$EmptyListName), :$valuePlaceholder)"
    case LeafPrependExpression(namePlaceholder, _, valuePlaceholder, _) =>
      s"#$namePlaceholder = list_append(:$valuePlaceholder, if_not_exists(#$namePlaceholder, :$EmptyListName))"
    case LeafRemoveExpression(namePlaceholder, _)  => s"#$namePlaceholder"
    case LeafAttributeExpression(prefix, from, to) => s"#${to.placeholder(prefix)} = #${from.placeholder(prefix)}"
    case LeafSetIfNotExistsExpression(namePlaceholder, _, valuePlaceholder, _) =>
      s"#$namePlaceholder = if_not_exists(#$namePlaceholder, :$valuePlaceholder)"
  }

  final def prefixKeys(prefix: String): LeafUpdateExpression =
    self match {
      case x: LeafSetExpression            => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
      case x: LeafAppendExpression         => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
      case x: LeafPrependExpression        => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
      case x: LeafAddExpression            => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
      case x: LeafDeleteExpression         => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
      case x: LeafSetIfNotExistsExpression => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
      case x                               => x
    }

  final val dynamoValue: Option[(String, DynamoValue)] = self match {
    case LeafAddExpression(_, _, valuePlaceholder, av)            => Some(valuePlaceholder -> av)
    case LeafAppendExpression(_, _, valuePlaceholder, av)         => Some(valuePlaceholder -> av)
    case LeafDeleteExpression(_, _, valuePlaceholder, av)         => Some(valuePlaceholder -> av)
    case LeafPrependExpression(_, _, valuePlaceholder, av)        => Some(valuePlaceholder -> av)
    case LeafSetExpression(_, _, valuePlaceholder, av)            => Some(valuePlaceholder -> av)
    case LeafSetIfNotExistsExpression(_, _, valuePlaceholder, av) => Some(valuePlaceholder -> av)
    case _                                                        => None
  }

  def attributeNames: Map[String, String]
}

private[update] object LeafUpdateExpression {
  val EmptyListName = "emptyList"
}

final private[update] case class LeafSetExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

final private[update] case class LeafAppendExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

final private[update] case class LeafPrependExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

final private[update] case class LeafAddExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

final private[update] case class LeafSetIfNotExistsExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

/*
Note the difference between DELETE and REMOVE:
 - DELETE is used to delete an element from a set
 - REMOVE is used to remove an attribute from an item
 */
final private[update] case class LeafDeleteExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

final private[update] case class LeafRemoveExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String]
) extends LeafUpdateExpression

final private[update] case class LeafAttributeExpression(
  prefix: String,
  from: AttributeName,
  to: AttributeName
) extends LeafUpdateExpression {
  final val attributeNames = to.attributeNames(prefix) ++ from.attributeNames(prefix)
}

object SetExpression {
  private val prefix = "updateSet"
  def apply[V](field: AttributeName, value: V)(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdate(
      LeafSetExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        format.write(value)
      )
    )
  def fromAttribute(from: AttributeName, to: AttributeName): UpdateExpression =
    SimpleUpdate(LeafAttributeExpression(prefix, from, to))
}

object SetIfNotExistsExpression {
  private val prefix = "updateSetIfNE"
  def apply[V](field: AttributeName, value: V)(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdate(
      LeafSetIfNotExistsExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        format.write(value)
      )
    )
}

object AppendExpression {
  private val prefix = "updateAppend"
  def apply[V: DynamoFormat](field: AttributeName, value: V): UpdateExpression =
    SimpleUpdate(
      LeafAppendExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        DynamoFormat.listFormat[V].write(List(value))
      )
    )
}

object PrependExpression {
  private val prefix = "updatePrepend"
  def apply[V: DynamoFormat](field: AttributeName, value: V): UpdateExpression =
    SimpleUpdate(
      LeafPrependExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        DynamoFormat.listFormat[V].write(List(value))
      )
    )
}

object AppendAllExpression {
  private val prefix = "updateAppendAll"
  def apply[V: DynamoFormat](field: AttributeName, value: List[V]): UpdateExpression =
    SimpleUpdate(
      LeafAppendExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        DynamoFormat.listFormat[V].write(value)
      )
    )
}

object PrependAllExpression {
  private val prefix = "updatePrependAll"
  def apply[V: DynamoFormat](field: AttributeName, value: List[V]): UpdateExpression =
    SimpleUpdate(
      LeafPrependExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        DynamoFormat.listFormat[V].write(value)
      )
    )
}

object AddExpression {
  private val prefix = "updateSet"
  def apply[V](field: AttributeName, value: V)(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdate(
      LeafAddExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        format.write(value)
      )
    )
}

object DeleteExpression {
  private val prefix = "updateDelete"
  def apply[V](field: AttributeName, value: V)(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdate(
      LeafDeleteExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        format.write(value)
      )
    )
}

object RemoveExpression {
  private val prefix = "updateRemove"
  def apply(field: AttributeName): UpdateExpression =
    SimpleUpdate(
      LeafRemoveExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix)
      )
    )
}
