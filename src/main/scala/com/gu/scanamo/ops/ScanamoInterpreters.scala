package com.gu.scanamo.ops

import cats._
import cats.syntax.either._
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBAsync}
import com.amazonaws.services.dynamodbv2.model.{UpdateItemResult, _}
import com.gu.scanamo.request.{ScanamoDeleteRequest, ScanamoPutRequest, ScanamoScanRequest, ScanamoUpdateRequest}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object ScanamoInterpreters {

  def id(client: AmazonDynamoDB) = new (ScanamoOpsA ~> Id) {
    def apply[A](op: ScanamoOpsA[A]): Id[A] = op match {
      case Put(req) =>
        client.putItem(JavaRequests.put(req))
      case ConditionalPut(req) =>
        Either.catchOnly[ConditionalCheckFailedException] {
          client.putItem(JavaRequests.put(req))
        }
      case Get(req) =>
        client.getItem(req)
      case Delete(req) =>
        client.deleteItem(JavaRequests.delete(req))
      case ConditionalDelete(req) =>
        Either.catchOnly[ConditionalCheckFailedException] {
          client.deleteItem(JavaRequests.delete(req))
        }
      case Scan(req) =>
        client.scan(JavaRequests.scan(req))
      case Query(req) =>
        client.query(req)
      case BatchWrite(req) =>
        client.batchWriteItem(req)
      case BatchGet(req) =>
        client.batchGetItem(req)
      case Update(req) =>
        client.updateItem(JavaRequests.update(req))
      case ConditionalUpdate(req) =>
        Either.catchOnly[ConditionalCheckFailedException] {
          client.updateItem(JavaRequests.update(req))
        }
    }
  }

  def future(client: AmazonDynamoDBAsync)(implicit ec: ExecutionContext) = new (ScanamoOpsA ~> Future) {
    private def futureOf[X <: AmazonWebServiceRequest, T](call: (X,AsyncHandler[X, T]) => java.util.concurrent.Future[T], req: X): Future[T] = {
      val p = Promise[T]()
      val h = new AsyncHandler[X, T] {
        def onError(exception: Exception) { p.complete(Failure(exception)); () }
        def onSuccess(request: X, result: T) { p.complete(Success(result)); () }
      }
      call(req, h)
      p.future
    }

    override def apply[A](op: ScanamoOpsA[A]): Future[A] = op match {
      case Put(req) =>
        futureOf(client.putItemAsync, JavaRequests.put(req))
      case ConditionalPut(req) =>
        futureOf(client.putItemAsync, JavaRequests.put(req))
          .map(Either.right[ConditionalCheckFailedException, PutItemResult])
          .recover {
            case e: ConditionalCheckFailedException => Either.left(e)
          }
      case Get(req) =>
        futureOf(client.getItemAsync, req)
      case Delete(req) =>
        futureOf(client.deleteItemAsync, JavaRequests.delete(req))
      case ConditionalDelete(req) =>
        futureOf(client.deleteItemAsync, JavaRequests.delete(req))
          .map(Either.right[ConditionalCheckFailedException, DeleteItemResult])
          .recover { case e: ConditionalCheckFailedException => Either.left(e) }
      case Scan(req) =>
        futureOf(client.scanAsync, JavaRequests.scan(req))
      case Query(req) =>
        futureOf(client.queryAsync, req)
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        futureOf(client.batchWriteItemAsync(_: BatchWriteItemRequest, _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]), req)
      case BatchGet(req) =>
        futureOf(client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]), req)
      case Update(req) =>
        futureOf(client.updateItemAsync, JavaRequests.update(req))
      case ConditionalUpdate(req) =>
        futureOf(client.updateItemAsync, JavaRequests.update(req))
          .map(Either.right[ConditionalCheckFailedException, UpdateItemResult])
          .recover {
            case e: ConditionalCheckFailedException => Either.left(e)
          }
    }
  }
}

private[ops] object JavaRequests {
  import collection.JavaConverters._

  def scan(req: ScanamoScanRequest): ScanRequest =
    req.index.foldLeft(
      req.options.exclusiveStartKey.foldLeft(
        req.options.limit.foldLeft(
          new ScanRequest().withTableName(req.tableName).withConsistentRead(req.options.consistent)
        )(_.withLimit(_))
      )((r, key) => r.withExclusiveStartKey(key.asJava))
    )(_.withIndexName(_))

  def put(req: ScanamoPutRequest): PutItemRequest =
    req.condition.foldLeft(
      new PutItemRequest().withTableName(req.tableName).withItem(req.item.getM)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(c.attributeNames.asJava)
      )((cond, values) => cond.withExpressionAttributeValues(values.asJava))
    )

  def delete(req: ScanamoDeleteRequest): DeleteItemRequest =
    req.condition.foldLeft(
      new DeleteItemRequest().withTableName(req.tableName).withKey(req.key.asJava)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(c.attributeNames.asJava)
      )((cond, values) => cond.withExpressionAttributeValues(values.asJava))
    )

  def update(req: ScanamoUpdateRequest): UpdateItemRequest = {
    val reqWithoutValues = req.condition.foldLeft(
      new UpdateItemRequest().withTableName(req.tableName).withKey(req.key.asJava)
        .withUpdateExpression(req.updateExpression)
        .withExpressionAttributeNames(req.attributeNames.asJava)
        .withReturnValues(ReturnValue.ALL_NEW)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(
          (c.attributeNames ++ req.attributeNames).asJava)
      )((cond, values) => cond.withExpressionAttributeValues(
        (values ++ req.attributeValues).asJava))
    )

    val attributeValues = req.condition.flatMap(_.attributeValues).foldLeft(req.attributeValues)(_ ++ _)
    if (attributeValues.isEmpty) reqWithoutValues
    else reqWithoutValues.withExpressionAttributeValues(attributeValues.asJava)
  }
}
