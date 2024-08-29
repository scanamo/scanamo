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
import org.scanamo.internal.aws.sdkv2.*
import org.scanamo.request.*
import org.scanamo.update.UpdateAndCondition
import software.amazon.awssdk.services.dynamodb.model
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.utils.builder.SdkBuilder

package object ops {
  type ScanamoOps[A] = Free[ScanamoOpsA, A]
  type ScanamoOpsT[M[_], A] = FreeT[ScanamoOpsA, M, A]

  /**   - ScanRequest - Option[filterExpression]
    *   - QueryRequest - keyConditionExpression + Option[filterExpression]
    *   - PutItemRequest - Option[conditionExpression]
    *   - DeleteItemRequest - Option[conditionExpression]
    *   - UpdateItemRequest - updateExpression + Option[conditionExpression]
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

    def baseSettings[T, B <: SdkBuilder[B, T]](as: AttributesSummation)(builder: B)(implicit
      h: HasExpressionAttributes[T, B]
    ): T =
      new HasExpressionAttributesOps[T, B](builder.set(as.tableName)(h.tableName)).attributes(as.attributes).build()

    def foof[T, B <: SdkBuilder[B, T]](as: AttributesSummation, updateAndCondition: UpdateAndCondition)(builder: B)(
      implicit h: HasUpdateAndCondition[T, B]
    ): T = baseSettings[T, B](as)(new HasUpdateAndConditionOps[T, B](builder).updateAndCondition(updateAndCondition))

    def scan(req: ScanamoScanRequest): ScanRequest = baseSettings[ScanRequest, ScanRequest.Builder](req)(
      ScanRequest.builder
        .setOpt(req.index)(_.indexName)
        .consistentRead(req.options.consistent)
        .setOpt(req.options.limit)(b => b.limit(_))
        .setOpt(req.options.exclusiveStartKey.map(_.toJavaMap))(_.exclusiveStartKey)
        .setOpt(req.options.filterCondition.map(_.expression))(_.filterExpression)
    )

    def query(req: ScanamoQueryRequest): QueryRequest = baseSettings[QueryRequest, QueryRequest.Builder](req)(
      QueryRequest.builder
        .setOpt(req.index)(_.indexName)
        .consistentRead(req.options.consistent)
        .setOpt(req.options.limit)(b => b.limit(_))
        .setOpt(req.options.exclusiveStartKey.map(_.toJavaMap))(_.exclusiveStartKey)
        .setOpt(req.options.filterCondition.map(_.expression))(_.filterExpression)
        .scanIndexForward(req.options.ascending)
        .keyConditionExpression(req.queryCondition.expression)
    )

    def put(req: ScanamoPutRequest): PutItemRequest = baseSettings[PutItemRequest, PutItemRequest.Builder](req)(
      PutItemRequest.builder
        .item(req.item.asObject.orEmpty.toJavaMap)
        .returnValues(req.ret.asDynamoValue)
        .setOpt(req.condition.map(_.expression))(_.conditionExpression)
    )

    def delete(req: ScanamoDeleteRequest): DeleteItemRequest =
      baseSettings[DeleteItemRequest, DeleteItemRequest.Builder](req)(
        DeleteItemRequest.builder
          .key(req.key.toJavaMap)
          .returnValues(req.ret.asDynamoValue)
          .setOpt(req.condition.map(_.expression))(_.conditionExpression)
      )

    def update(req: ScanamoUpdateRequest): UpdateItemRequest =
      foof[UpdateItemRequest, UpdateItemRequest.Builder](req, req.updateAndCondition)(
        UpdateItemRequest.builder.key(req.key.toJavaMap).returnValues(ReturnValue.ALL_NEW)
      )

    def transactItems(req: ScanamoTransactWriteRequest): TransactWriteItemsRequest = {
      val putItems = req.putItems.map { item =>
        TransactWriteItem.builder
          .put(
            baseSettings[model.Put, model.Put.Builder](item)(
              model.Put.builder
                .item(item.item.asObject.orEmpty.toJavaMap)
                .setOpt(item.condition.map(_.expression))(_.conditionExpression)
            )
          )
          .build
      }

      val updateItems = req.updateItems.map { item =>
        TransactWriteItem.builder
          .update(
            foof[model.Update, model.Update.Builder](item, item.updateAndCondition)(
              model.Update.builder.key(item.key.toJavaMap)
            )
          )
          .build
      }
      val deleteItems = req.deleteItems.map { item =>
        TransactWriteItem.builder
          .delete(
            baseSettings[model.Delete, model.Delete.Builder](item)(
              model.Delete.builder
                .key(item.key.toJavaMap)
                .setOpt(item.condition.map(_.expression))(_.conditionExpression)
            )
          )
          .build
      }

      val conditionChecks = req.conditionCheck.map { item =>
        TransactWriteItem.builder
          .conditionCheck(
            baseSettings[model.ConditionCheck, model.ConditionCheck.Builder](item)(
              model.ConditionCheck.builder
                .key(item.key.toJavaMap)
                .conditionExpression(item.condition.expression)
            )
          )
          .build
      }

      TransactWriteItemsRequest.builder
        .transactItems(putItems ++ updateItems ++ deleteItems ++ conditionChecks: _*)
        .build
    }
  }
}
