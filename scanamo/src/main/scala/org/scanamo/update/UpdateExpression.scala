package org.scanamo.update

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import cats.data.NonEmptyVector
import cats.kernel.Semigroup
import cats.kernel.instances.map._
import org.scanamo.{DynamoFormat, DynamoValue}
import org.scanamo.query._

sealed trait UpdateExpression extends Product with Serializable { self =>
  final def expression: String =
    typeExpressions.map {
      case (t, e) =>
        s"${t.op} ${e.map(_.expression).toVector.mkString(", ")}"
    }.mkString(" ")

  final def typeExpressions: Map[UpdateType, NonEmptyVector[LeafUpdateExpression]] = self match {
    case SimpleUpdateExpression(leaf) => Map(leaf.updateType -> NonEmptyVector.of(leaf))
    case AndUpdate(l, r) =>
      val semigroup = Semigroup[Map[UpdateType, NonEmptyVector[LeafUpdateExpression]]]
      val leftUpdates = l.typeExpressions.mapValues(_.map(_.prefixKeys("l_")))
      val rightUpdates = r.typeExpressions.mapValues(_.map(_.prefixKeys("r_")))

      semigroup.combine(leftUpdates, rightUpdates)
  }

  final def attributeNames: Map[String, String] =
    unprefixedAttributeNames.map {
      case (k, v) => (s"#$k", v)
    }

  final val constantValue: Option[(String, DynamoValue)] = self match {
    case SimpleUpdateExpression(leaf) => leaf.constantValue
    case AndUpdate(l, r)              => l.constantValue.orElse(r.constantValue)
  }

  final def unprefixedAttributeNames: Map[String, String] = self match {
    case SimpleUpdateExpression(leaf) => leaf.attributeNames
    case AndUpdate(l, r)              => l.unprefixedAttributeNames ++ r.unprefixedAttributeNames
  }

  final def dynamoValues: Map[String, DynamoValue] = unprefixedDynamoObject ++ constantValue.toMap

  final def attributeValues: Map[String, AttributeValue] = dynamoValues.mapValues(_.toAttributeValue)

  final def unprefixedDynamoObject: Map[String, DynamoValue] = self match {
    case SimpleUpdateExpression(leaf) => leaf.dynamoValue.toMap
    case AndUpdate(l, r) =>
      UpdateExpression.prefixKeys(l.unprefixedDynamoObject, "l_") ++ UpdateExpression
        .prefixKeys(r.unprefixedDynamoObject, "r_")
  }

  final def unprefixedAttributeValues: Map[String, AttributeValue] =
    unprefixedDynamoObject.mapValues(_.toAttributeValue)
}

private[scanamo] final case class SimpleUpdateExpression(leaf: LeafUpdateExpression) extends UpdateExpression
private[scanamo] final case class AndUpdate(l: UpdateExpression, r: UpdateExpression) extends UpdateExpression

object UpdateExpression {
  def prefixKeys[T](map: Map[String, T], prefix: String) = map.map {
    case (k, v) => (s"$prefix$k", v)
  }

  def set[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    SetExpression(fieldValue._1, fieldValue._2)
  def setFromAttribute(fields: (AttributeName, AttributeName)): UpdateExpression = {
    val (to, from) = fields
    SetExpression.fromAttribute(from, to)
  }
  def append[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    AppendExpression(fieldValue._1, fieldValue._2)
  def prepend[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    PrependExpression(fieldValue._1, fieldValue._2)
  def appendAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
    AppendAllExpression(fieldValue._1, fieldValue._2)
  def prependAll[V: DynamoFormat](fieldValue: (AttributeName, List[V])): UpdateExpression =
    PrependAllExpression(fieldValue._1, fieldValue._2)
  def add[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    AddExpression(fieldValue._1, fieldValue._2)
  def delete[V: DynamoFormat](fieldValue: (AttributeName, V)): UpdateExpression =
    DeleteExpression(fieldValue._1, fieldValue._2)
  def remove(field: AttributeName): UpdateExpression =
    RemoveExpression(field)

  implicit object Semigroup extends Semigroup[UpdateExpression] {
    override def combine(x: UpdateExpression, y: UpdateExpression): UpdateExpression = AndUpdate(x, y)
  }
}

private[update] sealed trait UpdateType { val op: String }
private[update] case object SET extends UpdateType { override val op = "SET" }
private[update] case object ADD extends UpdateType { override val op = "ADD" }
private[update] case object DELETE extends UpdateType { override val op = "DELETE" }
private[update] case object REMOVE extends UpdateType { override val op = "REMOVE" }

private[update] sealed trait LeafUpdateExpression { self =>
  final val updateType: UpdateType = self match {
    case _: LeafAddExpression       => ADD
    case _: LeafAppendExpression    => SET
    case _: LeafDeleteExpression    => DELETE
    case _: LeafPrependExpression   => SET
    case _: LeafRemoveExpression    => REMOVE
    case _: LeafSetExpression       => SET
    case _: LeafAttributeExpression => SET
  }

  final val constantValue: Option[(String, DynamoValue)] = self match {
    case _: LeafAppendExpression  => Some("emptyList" -> DynamoValue.array())
    case _: LeafPrependExpression => Some("emptyList" -> DynamoValue.array())
    case _                        => None
  }

  final def expression: String = self match {
    case LeafAddExpression(namePlaceholder, _, valuePlaceholder, _)    => s"#$namePlaceholder :$valuePlaceholder"
    case LeafDeleteExpression(namePlaceholder, _, valuePlaceholder, _) => s"#$namePlaceholder :$valuePlaceholder"
    case LeafSetExpression(namePlaceholder, _, valuePlaceholder, _)    => s"#$namePlaceholder = :$valuePlaceholder"
    case LeafAppendExpression(namePlaceholder, _, valuePlaceholder, _) =>
      s"#$namePlaceholder = list_append(if_not_exists(#$namePlaceholder, :emptyList), :$valuePlaceholder)"
    case LeafPrependExpression(namePlaceholder, _, valuePlaceholder, _) =>
      s"#$namePlaceholder = list_append(:$valuePlaceholder, if_not_exists(#$namePlaceholder, :emptyList))"
    case LeafRemoveExpression(namePlaceholder, _)  => s"#$namePlaceholder"
    case LeafAttributeExpression(prefix, from, to) => s"#${to.placeholder(prefix)} = #${from.placeholder(prefix)}"
  }

  final def prefixKeys(prefix: String): LeafUpdateExpression = self match {
    case x: LeafSetExpression     => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
    case x: LeafAppendExpression  => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
    case x: LeafPrependExpression => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
    case x: LeafAddExpression     => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
    case x: LeafDeleteExpression  => x.copy(valuePlaceholder = s"$prefix${x.valuePlaceholder}")
    case x                        => x
  }

  final val dynamoValue: Option[(String, DynamoValue)] = self match {
    case LeafAddExpression(_, _, valuePlaceholder, av)     => Some(valuePlaceholder -> av)
    case LeafAppendExpression(_, _, valuePlaceholder, av)  => Some(valuePlaceholder -> av)
    case LeafDeleteExpression(_, _, valuePlaceholder, av)  => Some(valuePlaceholder -> av)
    case LeafPrependExpression(_, _, valuePlaceholder, av) => Some(valuePlaceholder -> av)
    case LeafSetExpression(_, _, valuePlaceholder, av)     => Some(valuePlaceholder -> av)
    case _                                                 => None
  }

  def attributeNames: Map[String, String]
}

private[update] final case class LeafSetExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

private[update] case class LeafAppendExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

private[update] case class LeafPrependExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

private[update] case class LeafAddExpression(
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
private[update] case class LeafDeleteExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: DynamoValue
) extends LeafUpdateExpression

private[update] case class LeafRemoveExpression(
  namePlaceholder: String,
  attributeNames: Map[String, String]
) extends LeafUpdateExpression

private[update] case class LeafAttributeExpression(
  prefix: String,
  from: AttributeName,
  to: AttributeName
) extends LeafUpdateExpression {
  final val attributeNames = to.attributeNames(prefix) ++ from.attributeNames(prefix)
}

object SetExpression {
  private val prefix = "updateSet"
  def apply[V](field: AttributeName, value: V)(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdateExpression(
      LeafSetExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        format.write(value)
      )
    )
  def fromAttribute(from: AttributeName, to: AttributeName): UpdateExpression =
    SimpleUpdateExpression(LeafAttributeExpression(prefix, from, to))
}

object AppendExpression {
  private val prefix = "updateAppend"
  def apply[V](field: AttributeName, value: V)(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdateExpression(
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
  def apply[V](field: AttributeName, value: V)(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdateExpression(
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
  def apply[V](field: AttributeName, value: List[V])(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdateExpression(
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
  def apply[V](field: AttributeName, value: List[V])(implicit format: DynamoFormat[V]): UpdateExpression =
    SimpleUpdateExpression(
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
    SimpleUpdateExpression(
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
    SimpleUpdateExpression(
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
    SimpleUpdateExpression(
      LeafRemoveExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix)
      )
    )
}
