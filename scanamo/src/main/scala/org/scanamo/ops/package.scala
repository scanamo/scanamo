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
import org.scanamo.internal.aws.sdkv2.*
import org.scanamo.internal.aws.sdkv2.HasCondition.*
import org.scanamo.internal.aws.sdkv2.HasExpressionAttributes.*
import org.scanamo.internal.aws.sdkv2.HasUpdateAndCondition.*
import org.scanamo.request.*
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.model.ReturnValue.ALL_NEW
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

    def put(req: ScanamoPutRequest): PutItemRequest =
      baseWithOptCond[PutItemRequest, PutItemRequest.Builder](req)(
        PutItemRequest.builder.item(req).returnValues(req.ret.asDynamoValue)
      )

    def delete(req: ScanamoDeleteRequest): DeleteItemRequest =
      baseWithOptCond[DeleteItemRequest, DeleteItemRequest.Builder](req)(
        DeleteItemRequest.builder.key(req).returnValues(req.ret.asDynamoValue)
      )

    def update(req: ScanamoUpdateRequest): UpdateItemRequest =
      baseWithUpdate[UpdateItemRequest, UpdateItemRequest.Builder](req)(
        UpdateItemRequest.builder.key(req).returnValues(ALL_NEW)
      )

    import TransactionItems.*

    def transactItems(req: ScanamoTransactWriteRequest): TransactWriteItemsRequest = TransactWriteItemsRequest.builder
      .transactItems((req.putItems ++ req.updateItems ++ req.deleteItems ++ req.conditionCheck).map(_.toAwsSdk)*)
      .build
  }
}
