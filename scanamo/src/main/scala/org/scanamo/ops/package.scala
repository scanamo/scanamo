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

import cats.data.NonEmptyList
import cats.free.{ Free, FreeT }
import cats.implicits.*
import org.scanamo.request.*
import software.amazon.awssdk.services.dynamodb.model.*

package object ops {
  type ScanamoOps[A] = Free[ScanamoOpsA, A]
  type ScanamoOpsT[M[_], A] = FreeT[ScanamoOpsA, M, A]

  private[ops] object JavaRequests {
    import collection.JavaConverters.*

    def scan(req: ScanamoScanRequest): ScanRequest = {
      def queryRefinement[T](
        o: ScanamoScanRequest => Option[T]
      )(rt: (ScanRequest.Builder, T) => ScanRequest.Builder): ScanRequest.Builder => ScanRequest.Builder = { qr =>
        o(req).foldLeft(qr)(rt)
      }

      NonEmptyList
        .of(
          queryRefinement(_.index)(_.indexName(_)),
          queryRefinement(_.options.limit)(_.limit(_)),
          queryRefinement(_.options.exclusiveStartKey)((r, k) => r.exclusiveStartKey(k.toJavaMap)),
          queryRefinement(_.options.filter) { (r, f) =>
            val requestCondition = f.apply.runEmptyA.value
            val attributes = requestCondition.attributes
            val builder =
              r.filterExpression(requestCondition.expression).expressionAttributeNames(attributes.names.asJava)
            attributes.values.toExpressionAttributeValues.foldLeft(builder)(_ expressionAttributeValues _)
          }
        )
        .reduceLeft(_.compose(_))(
          ScanRequest.builder.tableName(req.tableName).consistentRead(req.options.consistent)
        )
        .build
    }

    def query(req: ScanamoQueryRequest): QueryRequest = {
      def queryRefinement[T](
        f: ScanamoQueryRequest => Option[T]
      )(g: (QueryRequest.Builder, T) => QueryRequest.Builder): QueryRequest.Builder => QueryRequest.Builder = { qr =>
        f(req).foldLeft(qr)(g)
      }

      val queryCondition: RequestCondition = req.query.apply
      val requestCondition: Option[RequestCondition] = req.options.filter.map(_.apply.runEmptyA.value)

      val requestBuilder = NonEmptyList
        .of(
          queryRefinement(_.index)(_ indexName _),
          queryRefinement(_.options.limit)(_ limit _),
          queryRefinement(_.options.exclusiveStartKey.map(_.toJavaMap))(_ exclusiveStartKey _)
        )
        .reduceLeft(_ compose _)(
          QueryRequest.builder
            .tableName(req.tableName)
            .consistentRead(req.options.consistent)
            .scanIndexForward(req.options.ascending)
            .keyConditionExpression(queryCondition.expression)
        )

      requestCondition.fold {
        val requestWithCondition = requestBuilder.expressionAttributeNames(queryCondition.attributes.names.asJava)
        queryCondition.attributes.values.toExpressionAttributeValues
          .foldLeft(requestWithCondition)(_ expressionAttributeValues _)
      } { condition =>
        val attributes = queryCondition.attributes |+| condition.attributes

        val requestWithCondition = requestBuilder
          .filterExpression(condition.expression)
          .expressionAttributeNames(attributes.names.asJava)
        attributes.values.toExpressionAttributeValues.foldLeft(requestWithCondition)(_ expressionAttributeValues _)
      }.build
    }

    def put(req: ScanamoPutRequest): PutItemRequest = {
      val request = PutItemRequest.builder
        .tableName(req.tableName)
        .item(req.item.asObject.getOrElse(DynamoObject.empty).toJavaMap)
        .returnValues(req.ret.asDynamoValue)

      req.condition
        .fold(request) { condition =>
          val requestWithCondition = request
            .conditionExpression(condition.expression)
            .expressionAttributeNames(condition.attributes.names.asJava)

          condition.attributes.values.toExpressionAttributeValues
            .foldLeft(requestWithCondition)(_ expressionAttributeValues _)
        }
        .build
    }

    def delete(req: ScanamoDeleteRequest): DeleteItemRequest = {
      val request = DeleteItemRequest.builder
        .tableName(req.tableName)
        .key(req.key.toJavaMap)
        .returnValues(req.ret.asDynamoValue)

      req.condition
        .fold(request) { condition =>
          val requestWithCondition = request
            .conditionExpression(condition.expression)
            .expressionAttributeNames(condition.attributes.names.asJava)

          condition.attributes.values.toExpressionAttributeValues
            .foldLeft(requestWithCondition)(_ expressionAttributeValues _)
        }
        .build
    }

    def update(req: ScanamoUpdateRequest): UpdateItemRequest = {
      val request = UpdateItemRequest.builder
        .tableName(req.tableName)
        .key(req.key.toJavaMap)
        .updateExpression(req.updateAndCondition.update.expression)
        .returnValues(ReturnValue.ALL_NEW)
        .expressionAttributeNames(req.updateAndCondition.attributes.names.asJava)

      val requestWithCondition =
        req.updateAndCondition.condition.fold(request)(condition => request.conditionExpression(condition.expression))

      req.updateAndCondition.attributes.values.toExpressionAttributeValues
        .fold(requestWithCondition)(requestWithCondition.expressionAttributeValues)
        .build
    }

    def transactItems(req: ScanamoTransactWriteRequest): TransactWriteItemsRequest = {
      val putItems = req.putItems.map { item =>
        TransactWriteItem.builder
          .put(
            software.amazon.awssdk.services.dynamodb.model.Put.builder
              .item(item.item.asObject.getOrElse(DynamoObject.empty).toJavaMap)
              .tableName(item.tableName)
              .build
          )
          .build
      }

      val updateItems = req.updateItems.map { item =>
        val update = software.amazon.awssdk.services.dynamodb.model.Update.builder
          .tableName(item.tableName)
          .updateExpression(item.updateAndCondition.update.expression)
          .expressionAttributeNames(item.updateAndCondition.update.attributes.names.asJava)
          .key(item.key.toJavaMap)

        val updateWithAvs = item.updateAndCondition.update.attributes.values.toExpressionAttributeValues
          .fold(update)(avs => update.expressionAttributeValues(avs))
          .build

        TransactWriteItem.builder.update(updateWithAvs).build
      }
      val deleteItems = req.deleteItems.map { item =>
        TransactWriteItem.builder
          .delete(
            software.amazon.awssdk.services.dynamodb.model.Delete.builder
              .key(item.key.toJavaMap)
              .tableName(item.tableName)
              .build
          )
          .build
      }

      val conditionChecks = req.conditionCheck.map { item =>
        val check = software.amazon.awssdk.services.dynamodb.model.ConditionCheck.builder
          .key(item.key.toJavaMap)
          .tableName(item.tableName)
          .conditionExpression(item.condition.expression)
          .expressionAttributeNames(item.condition.attributes.names.asJava)

        val checkWithAvs = item.condition.attributes.values.toExpressionAttributeValues
          .foldLeft(check)(_ expressionAttributeValues _)
          .build

        TransactWriteItem.builder.conditionCheck(checkWithAvs).build
      }

      TransactWriteItemsRequest.builder
        .transactItems((putItems ++ updateItems ++ deleteItems ++ conditionChecks): _*)
        .build
    }
  }
}
