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
import org.scanamo.internal.aws.sdkv2.*
import org.scanamo.request.*
import software.amazon.awssdk.services.dynamodb.model.*

package object ops {
  type ScanamoOps[A] = Free[ScanamoOpsA, A]
  type ScanamoOpsT[M[_], A] = FreeT[ScanamoOpsA, M, A]

  private[ops] object JavaRequests {
    import collection.JavaConverters.*

    def scan(req: ScanamoScanRequest): ScanRequest = {
      val filterCondition: Option[RequestCondition] = req.options.filter.map(_.apply.runEmptyA.value)

      ScanRequest.builder
        .tableName(req.tableName)
        .setOpt(req.index)(_.indexName)
        .consistentRead(req.options.consistent)
        .setOpt(req.options.limit)(b => b.limit(_))
        .setOpt(req.options.exclusiveStartKey.map(_.toJavaMap))(_.exclusiveStartKey)
        .setOpt(filterCondition) { b => condition =>
          b.filterExpression(condition.expression)
            .expressionAttributeNames(condition.attributes.names.asJava)
            .setOpt(condition.attributes.values.toExpressionAttributeValues)(_.expressionAttributeValues)
        }
        .build
    }

    def query(req: ScanamoQueryRequest): QueryRequest = {
      val queryCondition: RequestCondition = req.query.apply
      val filterCondition: Option[RequestCondition] = req.options.filter.map(_.apply.runEmptyA.value)
      val attributes = queryCondition.attributes |+| filterCondition.map(_.attributes).orEmpty

      QueryRequest.builder
        .tableName(req.tableName)
        .setOpt(req.index)(_.indexName)
        .consistentRead(req.options.consistent)
        .setOpt(req.options.limit)(b => b.limit(_))
        .setOpt(req.options.exclusiveStartKey.map(_.toJavaMap))(_.exclusiveStartKey)
        .setOpt(filterCondition.map(_.expression))(_.filterExpression)
        .expressionAttributeNames(attributes.names.asJava)
        .setOpt(attributes.values.toExpressionAttributeValues)(_.expressionAttributeValues)
        .scanIndexForward(req.options.ascending)
        .keyConditionExpression(queryCondition.expression)
        .build
    }

    def put(req: ScanamoPutRequest): PutItemRequest = PutItemRequest.builder
      .tableName(req.tableName)
      .item(req.item.asObject.orEmpty.toJavaMap)
      .returnValues(req.ret.asDynamoValue)
      .setOpt(req.condition) { b => condition =>
        b.conditionExpression(condition.expression)
          .expressionAttributeNames(condition.attributes.names.asJava)
          .setOpt(condition.attributes.values.toExpressionAttributeValues)(_.expressionAttributeValues)
      }
      .build

    def delete(req: ScanamoDeleteRequest): DeleteItemRequest = DeleteItemRequest.builder
      .tableName(req.tableName)
      .key(req.key.toJavaMap)
      .returnValues(req.ret.asDynamoValue)
      .setOpt(req.condition) { b => condition =>
        b.conditionExpression(condition.expression)
          .expressionAttributeNames(condition.attributes.names.asJava)
          .setOpt(condition.attributes.values.toExpressionAttributeValues)(_.expressionAttributeValues)
      }
      .build

    def update(req: ScanamoUpdateRequest): UpdateItemRequest = {
      val attributes = req.updateAndCondition.attributes
      UpdateItemRequest.builder
        .tableName(req.tableName)
        .key(req.key.toJavaMap)
        .returnValues(ReturnValue.ALL_NEW)
        .updateExpression(req.updateAndCondition.update.expression)
        .setOpt(req.updateAndCondition.condition.map(_.expression))(_.conditionExpression)
        .expressionAttributeNames(attributes.names.asJava)
        .setOpt(attributes.values.toExpressionAttributeValues)(_.expressionAttributeValues)
        .build
    }

    def transactItems(req: ScanamoTransactWriteRequest): TransactWriteItemsRequest = {
      val putItems = req.putItems.map { item =>
        TransactWriteItem.builder
          .put(
            software.amazon.awssdk.services.dynamodb.model.Put.builder
              .tableName(item.tableName)
              .item(item.item.asObject.orEmpty.toJavaMap)
              .build
          )
          .build
      }

      val updateItems = req.updateItems.map { item =>
        val attributes = item.updateAndCondition.update.attributes
        TransactWriteItem.builder
          .update(
            software.amazon.awssdk.services.dynamodb.model.Update.builder
              .tableName(item.tableName)
              .key(item.key.toJavaMap)
              .updateExpression(item.updateAndCondition.update.expression)
              .expressionAttributeNames(attributes.names.asJava)
              .setOpt(attributes.values.toExpressionAttributeValues)(_.expressionAttributeValues)
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
              .build
          )
          .build
      }

      val conditionChecks = req.conditionCheck.map { item =>
        val attributes = item.condition.attributes
        TransactWriteItem.builder
          .conditionCheck(
            software.amazon.awssdk.services.dynamodb.model.ConditionCheck.builder
              .tableName(item.tableName)
              .key(item.key.toJavaMap)
              .expressionAttributeNames(attributes.names.asJava)
              .setOpt(attributes.values.toExpressionAttributeValues)(_.expressionAttributeValues)
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
