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

package org.scanamo.request

import cats.implicits.*
import org.scanamo.internal.aws.sdkv2.BuilderStuff.BV
import org.scanamo.internal.aws.sdkv2.RichBuilder
import org.scanamo.query.{Condition, Query}
import org.scanamo.request.AWSSdkV2.{ANameMap, AValueMap}
import org.scanamo.update.{UpdateAndCondition, UpdateExpression}
import org.scanamo.{DeleteReturn, DynamoObject, DynamoValue, PutReturn}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.util
import scala.collection.JavaConverters.*

trait HasAttributes {
  def attributes: AttributeNamesAndValues
}

trait AttributesSummation {
  val tableName: String
  def attributesSources: Seq[HasAttributes]
  lazy val attributes: AttributeNamesAndValues =
    attributesSources.foldLeft(AttributeNamesAndValues.Empty)(_ |+| _.attributes)
}

trait WithOptionalCondition extends AttributesSummation {
  val condition: Option[RequestCondition]
}

/** We can try to think of this trait as being a Scala-optimised model of the idealised DynamoDB API model - ie it
  * reflects:
  *
  * https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.API.html#HowItWorks.API.DataPlane
  * https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Operations_Amazon_DynamoDB.html
  *
  * ...rather than the AWS SDK for Java:
  *
  * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/dynamodb/package-summary.html
  *
  * CRUD (Create, read, update and delete) in DynamoDB is Put, Get, Update, Delete - and actually in Scanamo we haven't
  * wrapped Get, so we are only concerned with Put, Update, Delete.
  *
  * All three of PUD are duplicated in DynamoDB - they have transactional *and* non-transactional forms that are almost
  * identical. It feels reasonable to give a common trait to both the transactional & non-transactional form of each
  * one.
  */
sealed trait CRUD extends Keyed

trait Putting extends CRUD with KeyedByItem with WithOptionalCondition {
  val attributesSources: Seq[HasAttributes] = condition.toSeq
}
trait Updating extends CRUD with KeyedByKey with AttributesSummation {
  val updateAndCondition: UpdateAndCondition

  val attributesSources: Seq[HasAttributes] = Seq(updateAndCondition)
}
trait Deleting extends CRUD with KeyedByKey with WithOptionalCondition {
  val attributesSources: Seq[HasAttributes] = condition.toSeq
}

/* For any SDK - eg SDK v1 or v2, maybe we could have an instance of DynamoSDK that can populate the common fields
 * of PUD requests...?
 *
 */

trait DynamoSDK {
  type ANameMap
  type AValueMap

  def forSdk(names: Map[String, String]): ANameMap
  def forSdk(values: DynamoObject): AValueMap

  trait HasKey[B] {
    val key: BV[B, AValueMap]
  }

  trait HasItem[B] {
    val item: BV[B, AValueMap]
  }

  trait HasExpressionAttributes[B] {
    type B2B[V] = BV[B, V]

    val tableName: B2B[String]
    val expressionAttributeNames: B2B[ANameMap]
    val expressionAttributeValues: B2B[AValueMap]
  }

  trait HasCondition[B] extends HasExpressionAttributes[B] {
    val conditionExpression: B2B[String]
  }

  trait HasUpdateAndCondition[B] extends HasCondition[B] {
    val updateExpression: B2B[String]
  }

  trait De[B] extends HasKey[B] with HasCondition[B]
  trait Up[B] extends HasKey[B] with HasUpdateAndCondition[B]
  trait Pu[B] extends HasItem[B] with HasCondition[B]

  implicit class HasExpressionAttributesOps[B](val b: B)(implicit h: HasExpressionAttributes[B]) {
    def tableName(name: String): B = h.tableName(b)(name)
    def expressionAttributeNames(names: ANameMap): B = h.expressionAttributeNames(b)(names)
    def expressionAttributeValues(values: AValueMap): B = h.expressionAttributeValues(b)(values)

    def attributes(attributes: AttributeNamesAndValues): B = if (attributes.isEmpty) b
    else
      b.expressionAttributeNames(forSdk(attributes.names))
        .setOpt(forSdk(attributes.values))(_.expressionAttributeValues)
  }

  implicit class HasKeyOps[B](val b: B)(implicit h: HasKey[B]) {
    def key(key: AValueMap): B = h.key(b)(key)
  }


  def delete[B: De](r: Deleting)(builder: B): B =
    builder.tableName(r.tableName).key(r.key).attributes(r.attributes)
}

object AWSSdkV2 extends DynamoSDK {
  override type ANameMap = util.Map[String, String]
  override type AValueMap = util.Map[String, AttributeValue]

  override def forSdk(names: Map[String, String]): ANameMap = names.asJava

  override def forSdk(values: DynamoObject): AValueMap =
    values.toExpressionAttributeValues
}

case class ScanamoPutRequest(
  tableName: String,
  item: DynamoValue,
  condition: Option[RequestCondition],
  ret: PutReturn
) extends Putting

case class ScanamoDeleteRequest(
  tableName: String,
  key: DynamoObject,
  condition: Option[RequestCondition],
  ret: DeleteReturn
) extends Deleting

case class ScanamoUpdateRequest(
  tableName: String,
  key: DynamoObject,
  updateAndCondition: UpdateAndCondition
) extends Updating {
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def updateExpression: String = updateAndCondition.update.expression
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def attributeNames: Map[String, String] = updateAndCondition.update.attributes.names
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def dynamoValues: DynamoObject = updateAndCondition.update.attributes.values
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def addEmptyList: Boolean = updateAndCondition.update.addEmptyList
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def condition: Option[RequestCondition] = updateAndCondition.condition
}

case class ScanamoScanRequest(
  tableName: String,
  index: Option[String],
  options: ScanamoQueryOptions
) extends AttributesSummation {
  lazy val attributesSources: Seq[HasAttributes] = options.filterCondition.toSeq
}

case class ScanamoQueryRequest(
  tableName: String,
  index: Option[String],
  query: Query[_],
  options: ScanamoQueryOptions
) extends AttributesSummation {
  lazy val queryCondition: RequestCondition = query.apply

  lazy val attributesSources: Seq[HasAttributes] = Seq(queryCondition) ++ options.filterCondition
}

case class ScanamoQueryOptions(
  consistent: Boolean,
  ascending: Boolean,
  limit: Option[Int],
  exclusiveStartKey: Option[DynamoObject],
  filter: Option[Condition[_]]
) {
  lazy val filterCondition: Option[RequestCondition] = filter.map(_.apply.runEmptyA.value)
}
object ScanamoQueryOptions {
  val default = ScanamoQueryOptions(consistent = false, ascending = true, None, None, None)
}

case class RequestCondition(
  expression: String,
  attributes: AttributeNamesAndValues
) extends HasAttributes {
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def this(
    expression: String,
    attributeNames: Map[String, String],
    dynamoValues: Option[DynamoObject]
  ) = this(expression, AttributeNamesAndValues(attributeNames, dynamoValues.orEmpty))
  @deprecated("Use `attributes.names` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def attributeNames: Map[String, String] = attributes.names
  @deprecated("Use `attributes.values` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def dynamoValues: Option[DynamoObject] = Some(attributes.values)
}

sealed trait TransactWriteAction

case class TransactPutItem(
  tableName: String,
  item: DynamoValue,
  condition: Option[RequestCondition]
) extends Putting
    with TransactWriteAction

case class TransactUpdateItem(
  tableName: String,
  key: DynamoObject,
  updateAndCondition: UpdateAndCondition
) extends Updating
    with TransactWriteAction {
  @deprecated("See https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def this(
    tableName: String,
    key: DynamoObject,
    updateExpression: UpdateExpression,
    condition: Option[RequestCondition]
  ) = this(tableName, key, UpdateAndCondition(updateExpression, condition))
  @deprecated("Use `updateAndCondition.update` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def updateExpression: UpdateExpression = updateAndCondition.update
  @deprecated("Use `updateAndCondition.condition` - see https://github.com/scanamo/scanamo/pull/1796", "3.0.0")
  def condition: Option[RequestCondition] = updateAndCondition.condition
}

case class TransactDeleteItem(
  tableName: String,
  key: DynamoObject,
  condition: Option[RequestCondition]
) extends WithOptionalCondition
    with TransactWriteAction
    with Deleting

case class TransactConditionCheck(
  tableName: String,
  key: DynamoObject,
  condition: RequestCondition
) extends AttributesSummation
    with TransactWriteAction
    with KeyedByKey {
  override val attributesSources: Seq[HasAttributes] = Seq(condition)
}

case class ScanamoTransactWriteRequest(
  putItems: Seq[TransactPutItem],
  updateItems: Seq[TransactUpdateItem],
  deleteItems: Seq[TransactDeleteItem],
  conditionCheck: Seq[TransactConditionCheck]
)
