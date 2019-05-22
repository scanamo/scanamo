package org.scanamo

import cats.free.Free
import cats.data.NonEmptyList
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
          queryRefinement(_.options.exclusiveStartKey)((r, k) => r.withExclusiveStartKey(k)),
          queryRefinement(_.options.filter)((r, f) => {
            val requestCondition = f.apply
            val filteredRequest = r
              .withFilterExpression(requestCondition.expression)
              .withExpressionAttributeNames(requestCondition.attributeNames.asJava)
            requestCondition.attributeValues
              .fold(filteredRequest)(avs => filteredRequest.withExpressionAttributeValues(avs.asJava))
          })
        )
        .reduceLeft(_.compose(_))(
          new ScanRequest().withTableName(req.tableName).withConsistentRead(req.options.consistent)
        )
    }

    def query(req: ScanamoQueryRequest): QueryRequest = {
      def queryRefinement[T](
        o: ScanamoQueryRequest => Option[T]
      )(rt: (QueryRequest, T) => QueryRequest): QueryRequest => QueryRequest = { qr =>
        o(req).foldLeft(qr)(rt)
      }

      NonEmptyList
        .of(
          queryRefinement(_.index)(_.withIndexName(_)),
          queryRefinement(_.options.limit)(_.withLimit(_)),
          queryRefinement(_.options.exclusiveStartKey)((r, k) => r.withExclusiveStartKey(k)),
          queryRefinement(_.options.filter)((r, f) => {
            val requestCondition = f.apply
            r.withFilterExpression(requestCondition.expression)
              .withExpressionAttributeNames(
                (r.getExpressionAttributeNames.asScala ++ requestCondition.attributeNames).asJava
              )
              .withExpressionAttributeValues(
                (r.getExpressionAttributeValues.asScala ++ requestCondition.attributeValues.getOrElse(Map.empty)).asJava
              )
          })
        )
        .reduceLeft(_.compose(_))(
          req.query(new QueryRequest().withTableName(req.tableName).withConsistentRead(req.options.consistent))
        )
    }

    def put(req: ScanamoPutRequest): PutItemRequest =
      req.condition.foldLeft(
        new PutItemRequest()
          .withTableName(req.tableName)
          .withItem(req.item.getM)
          .withReturnValues(ReturnValue.ALL_OLD)
      )(
        (r, c) =>
          c.attributeValues.foldLeft(
            r.withConditionExpression(c.expression).withExpressionAttributeNames(c.attributeNames.asJava)
          )((cond, values) => cond.withExpressionAttributeValues(values.asJava))
      )

    def delete(req: ScanamoDeleteRequest): DeleteItemRequest =
      req.condition.foldLeft(
        new DeleteItemRequest().withTableName(req.tableName).withKey(req.key.asJava)
      )(
        (r, c) =>
          c.attributeValues.foldLeft(
            r.withConditionExpression(c.expression).withExpressionAttributeNames(c.attributeNames.asJava)
          )((cond, values) => cond.withExpressionAttributeValues(values.asJava))
      )

    def update(req: ScanamoUpdateRequest): UpdateItemRequest = {
      val reqWithoutValues = req.condition.foldLeft(
        new UpdateItemRequest()
          .withTableName(req.tableName)
          .withKey(req.key.asJava)
          .withUpdateExpression(req.updateExpression)
          .withExpressionAttributeNames(req.attributeNames.asJava)
          .withReturnValues(ReturnValue.ALL_NEW)
      )(
        (r, c) =>
          c.attributeValues.foldLeft(
            r.withConditionExpression(c.expression)
              .withExpressionAttributeNames((c.attributeNames ++ req.attributeNames).asJava)
          )((cond, values) => cond.withExpressionAttributeValues((values ++ req.attributeValues).asJava))
      )

      val attributeValues = req.condition.flatMap(_.attributeValues).foldLeft(req.attributeValues)(_ ++ _)
      if (attributeValues.isEmpty) reqWithoutValues
      else reqWithoutValues.withExpressionAttributeValues(attributeValues.asJava)
    }
  }

}
