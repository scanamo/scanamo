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
import cats.syntax.apply.*
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
            requestCondition.dynamoValues
              .filter(_.nonEmpty)
              .flatMap(_.toExpressionAttributeValues)
              .foldLeft(
                r.filterExpression(requestCondition.expression)
                  .expressionAttributeNames(requestCondition.attributeNames.asJava)
              )(_ expressionAttributeValues _)
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
        val requestWithCondition = requestBuilder.expressionAttributeNames(queryCondition.attributeNames.asJava)
        queryCondition.dynamoValues
          .filter(_.nonEmpty)
          .flatMap(_.toExpressionAttributeValues)
          .foldLeft(requestWithCondition)(_ expressionAttributeValues _)
      } { condition =>
        val requestWithCondition = requestBuilder
          .filterExpression(condition.expression)
          .expressionAttributeNames((queryCondition.attributeNames ++ condition.attributeNames).asJava)
        val attributeValues =
          (
            queryCondition.dynamoValues orElse Some(DynamoObject.empty),
            condition.dynamoValues orElse Some(DynamoObject.empty)
          ).mapN(_ <> _)

        attributeValues
          .flatMap(_.toExpressionAttributeValues)
          .foldLeft(requestWithCondition)(_ expressionAttributeValues _)
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
            .expressionAttributeNames(condition.attributeNames.asJava)

          condition.dynamoValues
            .flatMap(_.toExpressionAttributeValues)
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
            .expressionAttributeNames(condition.attributeNames.asJava)

          condition.dynamoValues
            .flatMap(_.toExpressionAttributeValues)
            .foldLeft(requestWithCondition)(_ expressionAttributeValues _)
        }
        .build
    }

    def update(req: ScanamoUpdateRequest): UpdateItemRequest = {
      val attributeNames: Map[String, String] = req.condition.map(_.attributeNames).foldLeft(req.attributeNames)(_ ++ _)
      val attributeValues: DynamoObject = req.condition.flatMap(_.dynamoValues).foldLeft(req.dynamoValues)(_ <> _)
      val request = UpdateItemRequest.builder
        .tableName(req.tableName)
        .key(req.key.toJavaMap)
        .updateExpression(req.updateExpression)
        .returnValues(ReturnValue.ALL_NEW)
        .expressionAttributeNames(attributeNames.asJava)

      val requestWithCondition =
        req.condition.fold(request)(condition => request.conditionExpression(condition.expression))

      attributeValues.toExpressionAttributeValues
        .fold(requestWithCondition) { avs =>
          if (req.addEmptyList)
            avs.put(":emptyList", DynamoValue.EmptyList)
          requestWithCondition expressionAttributeValues avs
        }
        .build
    }

    def transactItems(req: ScanamoTransactWriteRequest): TransactWriteItemsRequest = {
      val putItems = req.putItems.map { item =>
        val putBuilder = software.amazon.awssdk.services.dynamodb.model.Put.builder
          .item(item.item.asObject.getOrElse(DynamoObject.empty).toJavaMap)
          .tableName(item.tableName)
        val putBuilderWithCondition = item.condition.fold(putBuilder)(condition =>
          putBuilder.conditionExpression(condition.expression).expressionAttributeNames(condition.attributeNames.asJava)
        )

        TransactWriteItem.builder
          .put(putBuilderWithCondition.build)
          .build
      }

      val updateItems = req.updateItems.map { item =>
        val update = software.amazon.awssdk.services.dynamodb.model.Update.builder
          .tableName(item.tableName)
          .updateExpression(item.updateExpression.expression)
          .expressionAttributeNames(item.updateExpression.attributeNames.asJava)
          .key(item.key.toJavaMap)

        val updateWithAvs = DynamoObject(item.updateExpression.dynamoValues).toExpressionAttributeValues
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
          .expressionAttributeNames(item.condition.attributeNames.asJava)

        val checkWithAvs = item.condition.dynamoValues
          .flatMap(_.toExpressionAttributeValues)
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
