package org.scanamo.update

import cats.data.NonEmptyVector
import cats.kernel.Semigroup
import cats.kernel.instances.map._
import org.scanamo.DynamoFormat
import org.scanamo.aws.models.AmazonAttribute
import org.scanamo.query._

sealed abstract class UpdateExpression[A: AmazonAttribute] extends Product with Serializable {
  def expression: String =
    typeExpressions.map {
      case (t, e) =>
        s"${t.op} ${e.map(_.expression).toVector.mkString(", ")}"
    }.mkString(" ")
  def typeExpressions: Map[UpdateType, NonEmptyVector[LeafUpdateExpression[A]]]
  def attributeNames: Map[String, String] =
    unprefixedAttributeNames.map {
      case (k, v) => (s"#$k", v)
    }
  val constantValue: Option[(String, A)]
  def unprefixedAttributeNames: Map[String, String]
  def attributeValues: Map[String, A] =
    (unprefixedAttributeValues ++ constantValue.toMap).map {
      case (k, v) => (s":$k", v)
    }
  def unprefixedAttributeValues: Map[String, A]
}

private[update] abstract class LeafUpdateExpression[A: AmazonAttribute] {
  val updateType: UpdateType
  val attributeNames: Map[String, String]
  val attributeValue: Option[(String, A)]
  val constantValue: Option[(String, A)]
  def expression: String
  def prefixKeys(prefix: String): LeafUpdateExpression[A]
}

object UpdateExpression {
  def set[V: DynamoFormat, A: AmazonAttribute](fieldValue: (AttributeName, V))(implicit dynamoFormat: DynamoFormat[V, A]): UpdateExpression[A] =
    SetExpression(fieldValue._1, fieldValue._2)
  def setFromAttribute[A: AmazonAttribute](fields: (AttributeName, AttributeName)): UpdateExpression[A] = {
    val (to, from) = fields
    SetExpression.fromAttribute[A](from, to)
  }
  def append[V: DynamoFormat, A: AmazonAttribute](fieldValue: (AttributeName, V))(implicit dynamoFormat: DynamoFormat[V, A]): UpdateExpression[A] =
    AppendExpression(fieldValue._1, fieldValue._2)
  def prepend[V: DynamoFormat, A: AmazonAttribute](fieldValue: (AttributeName, V))(implicit dynamoFormat: DynamoFormat[V, A]): UpdateExpression[A] =
    PrependExpression(fieldValue._1, fieldValue._2)
  def appendAll[V: DynamoFormat, A: AmazonAttribute](fieldValue: (AttributeName, List[V]))(implicit dynamoFormat: DynamoFormat[V, A]): UpdateExpression[A] =
    AppendAllExpression(fieldValue._1, fieldValue._2)
  def prependAll[V: DynamoFormat, A: AmazonAttribute](fieldValue: (AttributeName, List[V]))(implicit dynamoFormat: DynamoFormat[V, A]): UpdateExpression[A] =
    PrependAllExpression(fieldValue._1, fieldValue._2)
  def add[V: DynamoFormat, A: AmazonAttribute](fieldValue: (AttributeName, V))(implicit dynamoFormat: DynamoFormat[V, A]): UpdateExpression[A] =
    AddExpression(fieldValue._1, fieldValue._2)
  def delete[V: DynamoFormat, A: AmazonAttribute](fieldValue: (AttributeName, V))(implicit dynamoFormat: DynamoFormat[V, A]): UpdateExpression[A] =
    DeleteExpression(fieldValue._1, fieldValue._2)
  def remove[A: AmazonAttribute](field: AttributeName): UpdateExpression[A] =
    RemoveExpression(field)

  implicit object SemigroupV1 extends Semigroup[UpdateExpression[com.amazonaws.services.dynamodbv2.model.AttributeValue]] {
    override def combine(x: UpdateExpression[com.amazonaws.services.dynamodbv2.model.AttributeValue], y: UpdateExpression[com.amazonaws.services.dynamodbv2.model.AttributeValue]): UpdateExpression[com.amazonaws.services.dynamodbv2.model.AttributeValue] = AndUpdate(x, y)
  }
}

private[update] sealed trait UpdateType { val op: String }
private[update] case object SET extends UpdateType { override val op = "SET" }
private[update] case object ADD extends UpdateType { override val op = "ADD" }
private[update] case object DELETE extends UpdateType { override val op = "DELETE" }
private[update] case object REMOVE extends UpdateType { override val op = "REMOVE" }

private[update] case class SimpleUpdateExpression[A : AmazonAttribute](leaf: LeafUpdateExpression[A]) extends UpdateExpression[A] {
  override def typeExpressions: Map[UpdateType, NonEmptyVector[LeafUpdateExpression[A]]] =
    Map(leaf.updateType -> NonEmptyVector.of(leaf))
  override def unprefixedAttributeNames: Map[String, String] =
    leaf.attributeNames
  override def unprefixedAttributeValues: Map[String, A] =
    leaf.attributeValue.toMap
  override val constantValue: Option[(String, A)] = leaf.constantValue
}

private[update] case class LeafSetExpression[A: AmazonAttribute](
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: A
) extends LeafUpdateExpression[A] {
  override val updateType = SET
  override val constantValue = None
  override val attributeValue = Some(valuePlaceholder -> av)
  override def expression: String = s"#$namePlaceholder = :$valuePlaceholder"
  override def prefixKeys(prefix: String): LeafUpdateExpression[A] =
    LeafSetExpression(
      namePlaceholder,
      attributeNames,
      s"$prefix$valuePlaceholder",
      av
    )
}

object SetExpression {
  private val prefix = "updateSet"
  def apply[V, A: AmazonAttribute](field: AttributeName, value: V)(implicit format: DynamoFormat[V, A]): UpdateExpression[A] =
    SimpleUpdateExpression(
      LeafSetExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        format.write(value)
      )
    )
  def fromAttribute[A: AmazonAttribute](from: AttributeName, to: AttributeName): UpdateExpression[A] =
    SimpleUpdateExpression[A](new LeafUpdateExpression[A] {
      override def expression: String = s"#${to.placeholder(prefix)} = #${from.placeholder(prefix)}"

      override def prefixKeys(prefix: String): LeafUpdateExpression[A] = this

      override val constantValue: Option[(String, A)] = None
      override val attributeNames: Map[String, String] = to.attributeNames(prefix) ++ from.attributeNames(prefix)
      override val updateType: UpdateType = SET
      override val attributeValue: Option[(String, A)] = None
    })
}

private[update] case class LeafAppendExpression[A: AmazonAttribute](
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: A
)(implicit attrHelper: AmazonAttribute[A]) extends LeafUpdateExpression[A] {
  override val updateType = SET
  override val constantValue = Some("emptyList" -> attrHelper.setList(attrHelper.init)(Nil))
  override val attributeValue = Some(valuePlaceholder -> av)
  override def expression: String =
    s"#$namePlaceholder = list_append(if_not_exists(#$namePlaceholder, :emptyList), :$valuePlaceholder)"
  override def prefixKeys(prefix: String): LeafUpdateExpression[A] =
    LeafAppendExpression(
      namePlaceholder,
      attributeNames,
      s"$prefix$valuePlaceholder",
      av
    )
}

object AppendExpression {
  private val prefix = "updateAppend"
  def apply[V, A: AmazonAttribute](field: AttributeName, value: V)(implicit format: DynamoFormat[V, A]): UpdateExpression[A] =
    SimpleUpdateExpression(
      LeafAppendExpression(
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        DynamoFormat.listFormat[V].write(List(value))
      )
    )
}

private[update] case class LeafPrependExpression[A: AmazonAttribute](
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: A
)(implicit val avHelper: AmazonAttribute[A]) extends LeafUpdateExpression[A] {
  override val updateType = SET
  override val constantValue = {
    Some("emptyList" -> avHelper.setList(avHelper.init)(Nil))
  }
  override val attributeValue = Some(valuePlaceholder -> av)
  override def expression: String =
    s"#$namePlaceholder = list_append(:$valuePlaceholder, if_not_exists(#$namePlaceholder, :emptyList))"
  override def prefixKeys(prefix: String): LeafUpdateExpression[A] =
    LeafPrependExpression[A](
      namePlaceholder,
      attributeNames,
      s"$prefix$valuePlaceholder",
      av
    )
}

object PrependExpression {
  private val prefix = "updatePrepend"
  def apply[V, A: AmazonAttribute](field: AttributeName, value: V)(implicit format: DynamoFormat[V, A]): UpdateExpression[A] =
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
  def apply[V, A: AmazonAttribute](field: AttributeName, value: List[V])(implicit format: DynamoFormat[V, A]): UpdateExpression[A] =
    SimpleUpdateExpression[A](
      LeafAppendExpression[A](
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        DynamoFormat.listFormat[V].write(value)
      )
    )
}

object PrependAllExpression {
  private val prefix = "updatePrependAll"
  def apply[V, A: AmazonAttribute](field: AttributeName, value: List[V])(implicit format: DynamoFormat[V, A]): UpdateExpression[A] =
    SimpleUpdateExpression[A](
      LeafPrependExpression[A](
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        DynamoFormat.listFormat[V].write(value)
      )
    )
}

private[update] case class LeafAddExpression[A: AmazonAttribute](
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: A
) extends LeafUpdateExpression[A] {
  override val updateType = ADD
  override val constantValue = None
  override val attributeValue = Some(valuePlaceholder -> av)
  override def expression: String = s"#$namePlaceholder :$valuePlaceholder"
  override def prefixKeys(prefix: String): LeafUpdateExpression[A] =
    LeafAddExpression[A](
      namePlaceholder,
      attributeNames,
      s"$prefix$valuePlaceholder",
      av
    )
}

object AddExpression {
  private val prefix = "updateSet"
  def apply[A: AmazonAttribute, V](field: AttributeName, value: V)(implicit format: DynamoFormat[V, A]): UpdateExpression[A] =
    SimpleUpdateExpression[A](
      LeafAddExpression[A](
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        format.write(value)
      )
    )
}

/*
Note the difference between DELETE and REMOVE:
 - DELETE is used to delete an element from a set
 - REMOVE is used to remove an attribute from an item
 */
private[update] case class LeafDeleteExpression[A: AmazonAttribute](
  namePlaceholder: String,
  attributeNames: Map[String, String],
  valuePlaceholder: String,
  av: A
) extends LeafUpdateExpression[A] {
  override val updateType = DELETE
  override val constantValue = None
  override val attributeValue = Some(valuePlaceholder -> av)
  override def expression: String = s"#$namePlaceholder :$valuePlaceholder"
  override def prefixKeys(prefix: String): LeafUpdateExpression[A] =
    LeafDeleteExpression[A](
      namePlaceholder,
      attributeNames,
      s"$prefix$valuePlaceholder",
      av
    )
}

object DeleteExpression {
  private val prefix = "updateDelete"
  def apply[A: AmazonAttribute, V](field: AttributeName, value: V)(implicit format: DynamoFormat[V, A]): UpdateExpression[A] =
    SimpleUpdateExpression[A](
      LeafDeleteExpression[A](
        field.placeholder(prefix),
        field.attributeNames(prefix),
        "update",
        format.write(value)
      )
    )
}

private[update] case class LeafRemoveExpression[A: AmazonAttribute](
  namePlaceholder: String,
  attributeNames: Map[String, String]
) extends LeafUpdateExpression {
  override val updateType = REMOVE
  override val constantValue = None
  override val attributeValue = None
  override def expression: String = s"#$namePlaceholder"
  override def prefixKeys(prefix: String): LeafUpdateExpression[A] =
    LeafRemoveExpression[A](
      namePlaceholder,
      attributeNames
    )
}

object RemoveExpression {
  private val prefix = "updateRemove"
  def apply[A: AmazonAttribute](field: AttributeName): UpdateExpression[A] =
    SimpleUpdateExpression[A](
      LeafRemoveExpression[A](
        field.placeholder(prefix),
        field.attributeNames(prefix)
      )
    )
}

case class AndUpdate[A: AmazonAttribute](l: UpdateExpression[A], r: UpdateExpression[A]) extends UpdateExpression[A] {

  def prefixKeys[T](map: Map[String, T], prefix: String) = map.map {
    case (k, v) => (s"$prefix$k", v)
  }

  private val semigroup = Semigroup[Map[UpdateType, NonEmptyVector[LeafUpdateExpression[A]]]]

  override def typeExpressions: Map[UpdateType, NonEmptyVector[LeafUpdateExpression[A]]] = {
    val leftUpdates = l.typeExpressions.mapValues(_.map(_.prefixKeys("l_")))
    val rightUpdates = r.typeExpressions.mapValues(_.map(_.prefixKeys("r_")))

    semigroup.combine(leftUpdates, rightUpdates)
  }

  override val constantValue: Option[(String, A)] = l.constantValue.orElse(r.constantValue)

  override def unprefixedAttributeNames: Map[String, String] =
    l.unprefixedAttributeNames ++ r.unprefixedAttributeNames

  override def unprefixedAttributeValues: Map[String, A] =
    prefixKeys(l.unprefixedAttributeValues, "l_") ++ prefixKeys(r.unprefixedAttributeValues, "r_")
}
