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

package org.scanamo

import cats.free.{ Free, FreeT }
import cats.implicits.*
import org.scanamo.internal.aws.sdkv2.HasCondition.*
import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.*
import org.scanamo.internal.aws.sdkv2.HasUpdateAndCondition.*
import org.scanamo.internal.aws.sdkv2.RichBuilder
import org.scanamo.internal.aws.sdkv2.*
import org.scanamo.request.*
import software.amazon.awssdk.services.dynamodb.model.*

package object ops {
  type ScanamoOps[A] = Free[ScanamoOpsA, A]
  type ScanamoOpsT[M[_], A] = FreeT[ScanamoOpsA, M, A]

  /** ScanRequest - Option[filterExpression] QueryRequest - keyConditionExpression + Option[filterExpression]
    * PutItemRequest - Option[conditionExpression] DeleteItemRequest - Option[conditionExpression] UpdateItemRequest -
    * updateExpression + Option[conditionExpression]
    *
    * TransactWriteItemsRequest
    *   - Put - Option[conditionExpression]
    *   - Delete - Option[conditionExpression]
    *   - Update - updateExpression + Option[conditionExpression]
    *   - ConditionCheck - conditionExpression
    *
    * filterExpression - Condition[_] -> RequestCondition keyConditionExpression - Query[_] -> RequestCondition
    * conditionExpression - RequestCondition updateExpression - UpdateExpression (has attributes) projectionExpression ?
    */
  private[ops] object JavaRequests {
//    def funk[T, B <: Moo[T]](as: AttributesSummation, b: B)(implicit h: HasExpressionAttributes[B]): T =
//      b.tableName(as.tableName).attributes(as.attributes).build()

    def scan(req: ScanamoScanRequest): ScanRequest = {
      val builder: ScanRequest.Builder = ScanRequest.builder
        .setOpt(req.index)(_.indexName)
        .consistentRead(req.options.consistent)
        .setOpt(req.options.limit)(b => b.limit(_))
        .setOpt(req.options.exclusiveStartKey.map(_.toJavaMap))(_.exclusiveStartKey)
        .setOpt(req.options.filterCondition.map(_.expression))(_.filterExpression)

      val value = new RichBuilder2[ScanRequest, ScanRequest.Builder](builder)
      value.funk(req)

      // builder.funk(req)(HasExpressionAttributes.sr)
    }

    def query(req: ScanamoQueryRequest): QueryRequest = QueryRequest.builder
      .tableName(req.tableName)
      .setOpt(req.index)(_.indexName)
      .consistentRead(req.options.consistent)
      .setOpt(req.options.limit)(b => b.limit(_))
      .setOpt(req.options.exclusiveStartKey.map(_.toJavaMap))(_.exclusiveStartKey)
      .setOpt(req.options.filterCondition.map(_.expression))(_.filterExpression)
      .attributes(req.attributes)
      .scanIndexForward(req.options.ascending)
      .keyConditionExpression(req.queryCondition.expression)
      .build

    def put(req: ScanamoPutRequest): PutItemRequest = PutItemRequest.builder
      .tableName(req.tableName)
      .item(req.item.asObject.orEmpty.toJavaMap)
      .returnValues(req.ret.asDynamoValue)
      .attributes(req.attributes)
      .setOpt(req.condition.map(_.expression))(_.conditionExpression)
      .build

    def delete(req: ScanamoDeleteRequest): DeleteItemRequest = DeleteItemRequest.builder
      .tableName(req.tableName)
      .key(req.key.toJavaMap)
      .returnValues(req.ret.asDynamoValue)
      .attributes(req.attributes)
      .setOpt(req.condition.map(_.expression))(_.conditionExpression)
      .build

    def update(req: ScanamoUpdateRequest): UpdateItemRequest =
      UpdateItemRequest.builder
        .tableName(req.tableName)
        .key(req.key.toJavaMap)
        .returnValues(ReturnValue.ALL_NEW)
        .updateAndCondition(req.updateAndCondition)
        .build

    def transactItems(req: ScanamoTransactWriteRequest): TransactWriteItemsRequest = {
      val putItems = req.putItems.map { item =>
        TransactWriteItem.builder
          .put(
            software.amazon.awssdk.services.dynamodb.model.Put.builder
              .tableName(item.tableName)
              .item(item.item.asObject.orEmpty.toJavaMap)
              .attributes(item.attributes)
              .setOpt(item.condition.map(_.expression))(_.conditionExpression)
              .build
          )
          .build
      }

      val updateItems = req.updateItems.map { item =>
        TransactWriteItem.builder
          .update(
            software.amazon.awssdk.services.dynamodb.model.Update.builder
              .tableName(item.tableName)
              .key(item.key.toJavaMap)
              .updateAndCondition(item.updateAndCondition)
              .build
          )
          .build
      }
      val deleteItems = req.deleteItems.map { item =>
        TransactWriteItem.builder
          .delete(
            software.amazon.awssdk.services.dynamodb.model.Delete.builder
              .tableName(item.tableName)
              .key(item.key.toJavaMap)
              .attributes(item.attributes)
              .setOpt(item.condition.map(_.expression))(_.conditionExpression)
              .build
          )
          .build
      }

      val conditionChecks = req.conditionCheck.map { item =>
        TransactWriteItem.builder
          .conditionCheck(
            software.amazon.awssdk.services.dynamodb.model.ConditionCheck.builder
              .tableName(item.tableName)
              .key(item.key.toJavaMap)
              .attributes(item.attributes)
              .conditionExpression(item.condition.expression)
              .build
          )
          .build
      }

      TransactWriteItemsRequest.builder
        .transactItems(putItems ++ updateItems ++ deleteItems ++ conditionChecks: _*)
        .build
    }
  }
}
