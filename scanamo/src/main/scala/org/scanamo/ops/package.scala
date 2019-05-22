package org.scanamo

import cats.free.Free
import cats.data.NonEmptyList
import cats.instances.option._
import cats.syntax.apply._
import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.request._

package object ops {
  type ScanamoOps[A] = Free[ScanamoOpsA, A]

  private[ops] object JavaRequests {
    import collection.JavaConverters._

    def scan(req: ScanamoScanRequest): ScanRequest = {
      def queryRefinement[T](
        o: ScanamoScanRequest => Option[T]
      )(rt: (ScanRequest, T) => ScanRequest): ScanRequest => ScanRequest = { qr =>
        o(req).foldLeft(qr)(rt)
      }

      NonEmptyList
        .of(
          queryRefinement(_.index)(_.withIndexName(_)),
          queryRefinement(_.options.limit)(_.withLimit(_)),
          queryRefinement(_.options.exclusiveStartKey)((r, k) => r.withExclusiveStartKey(k.toJavaMap)),
          queryRefinement(_.options.filter)((r, f) => {
            val requestCondition = f.apply
            requestCondition.dynamoValues
              .filter(_.nonEmpty)
              .flatMap(_.toExpressionAttributeValues)
              .foldLeft(
                r.withFilterExpression(requestCondition.expression)
                  .withExpressionAttributeNames(requestCondition.attributeNames.asJava)
              )(_ withExpressionAttributeValues _)
          })
        )
        .reduceLeft(_.compose(_))(
          new ScanRequest().withTableName(req.tableName).withConsistentRead(req.options.consistent)
        )
    }

    def query(req: ScanamoQueryRequest): QueryRequest = {
      def queryRefinement[T](
        f: ScanamoQueryRequest => Option[T]
      )(g: (QueryRequest, T) => QueryRequest): QueryRequest => QueryRequest = { qr =>
        f(req).foldLeft(qr)(g)
      }

      val queryCondition: RequestCondition = req.query.apply
      val requestCondition: Option[RequestCondition] = req.options.filter.map(_.apply)

      val request = NonEmptyList
        .of(
          queryRefinement(_.index)(_ withIndexName _),
          queryRefinement(_.options.limit)(_ withLimit _),
          queryRefinement(_.options.exclusiveStartKey.map(_.toJavaMap))(_ withExclusiveStartKey _)
        )
        .reduceLeft(_ compose _)(
          new QueryRequest()
            .withTableName(req.tableName)
            .withConsistentRead(req.options.consistent)
            .withScanIndexForward(req.options.ascending)
            .withKeyConditionExpression(queryCondition.expression)
        )

      requestCondition.fold {
        val requestWithCondition = request.withExpressionAttributeNames(queryCondition.attributeNames.asJava)
        queryCondition.dynamoValues
          .filter(_.nonEmpty)
          .flatMap(_.toExpressionAttributeValues)
          .foldLeft(requestWithCondition)(_ withExpressionAttributeValues _)
      } { condition =>
        val requestWithCondition = request
          .withFilterExpression(condition.expression)
          .withExpressionAttributeNames((queryCondition.attributeNames ++ condition.attributeNames).asJava)
        val attributeValues =
          (
            queryCondition.dynamoValues orElse Some(DynamoObject.empty),
            condition.dynamoValues orElse Some(DynamoObject.empty)
          ).mapN(_ <> _)

        attributeValues
          .flatMap(_.toExpressionAttributeValues)
          .foldLeft(requestWithCondition)(_ withExpressionAttributeValues _)
      }
    }

    def put(req: ScanamoPutRequest): PutItemRequest = {
      val request = new PutItemRequest()
        .withTableName(req.tableName)
        .withItem(req.item.asObject.getOrElse(DynamoObject.empty).toJavaMap)
        .withReturnValues(ReturnValue.ALL_OLD)

      req.condition.fold(request) { condition =>
        val requestWithCondition = request
          .withConditionExpression(condition.expression)
          .withExpressionAttributeNames(condition.attributeNames.asJava)

        condition.dynamoValues
          .flatMap(_.toExpressionAttributeValues)
          .foldLeft(requestWithCondition)(_ withExpressionAttributeValues _)
      }
    }

    def delete(req: ScanamoDeleteRequest): DeleteItemRequest = {
      val request = new DeleteItemRequest().withTableName(req.tableName).withKey(req.key.toJavaMap)
      req.condition.fold(request) { condition =>
        val requestWithCondition = request
          .withConditionExpression(condition.expression)
          .withExpressionAttributeNames(condition.attributeNames.asJava)

        condition.dynamoValues
          .flatMap(_.toExpressionAttributeValues)
          .foldLeft(requestWithCondition)(_ withExpressionAttributeValues _)
      }
    }

    def update(req: ScanamoUpdateRequest): UpdateItemRequest = {
      val attributeNames: Map[String, String] = req.condition.map(_.attributeNames).foldLeft(req.attributeNames)(_ ++ _)
      val attributeValues: DynamoObject = req.condition.flatMap(_.dynamoValues).foldLeft(req.dynamoValues)(_ <> _)
      val request = new UpdateItemRequest()
        .withTableName(req.tableName)
        .withKey(req.key.toJavaMap)
        .withUpdateExpression(req.updateExpression)
        .withReturnValues(ReturnValue.ALL_NEW)
        .withExpressionAttributeNames(attributeNames.asJava)

      val requestWithCondition =
        req.condition.fold(request)(condition => request.withConditionExpression(condition.expression))

      attributeValues.toExpressionAttributeValues.fold(requestWithCondition) { avs =>
        if (req.addEmptyList) {
          avs.put(":emptyList", DynamoValue.EmptyList.toAttributeValue)
        }
        requestWithCondition withExpressionAttributeValues avs
      }
    }
  }

}
