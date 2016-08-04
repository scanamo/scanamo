package com.gu.scanamo.ops

import cats._
import cats.data.Xor
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBAsync}
import com.amazonaws.services.dynamodbv2.model.{UpdateItemResult, _}
import com.gu.scanamo.request.{ScanamoDeleteRequest, ScanamoPutRequest, ScanamoUpdateRequest}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object ScanamoInterpreters {
  import collection.convert.decorateAsJava._

  def javaPutRequest(req: ScanamoPutRequest): PutItemRequest =
    req.condition.foldLeft(
      new PutItemRequest().withTableName(req.tableName).withItem(req.item.getM)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(c.attributeNames.asJava)
      )((cond, values) => cond.withExpressionAttributeValues(values.asJava))
    )

  def javaDeleteRequest(req: ScanamoDeleteRequest): DeleteItemRequest =
    req.condition.foldLeft(
      new DeleteItemRequest().withTableName(req.tableName).withKey(req.key.asJava)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(c.attributeNames.asJava)
      )((cond, values) => cond.withExpressionAttributeValues(values.asJava))
    )

  def javaUpdateRequest(req: ScanamoUpdateRequest): UpdateItemRequest =
    req.condition.foldLeft(
      new UpdateItemRequest().withTableName(req.tableName).withKey(req.key.asJava)
        .withUpdateExpression(req.updateExpression)
        .withExpressionAttributeNames(req.attributeNames.asJava)
        .withExpressionAttributeValues(req.attributeValues.asJava)
    )((r, c) =>
      c.attributeValues.foldLeft(
        r.withConditionExpression(c.expression).withExpressionAttributeNames(
          (c.attributeNames ++ req.attributeNames).asJava)
      )((cond, values) => cond.withExpressionAttributeValues(
        (values ++ req.attributeValues).asJava))
    )

  def id(client: AmazonDynamoDB) = new (ScanamoOpsA ~> Id) {
    def apply[A](op: ScanamoOpsA[A]): Id[A] = op match {
      case Put(req) =>
        client.putItem(javaPutRequest(req))
      case ConditionalPut(req) =>
        Xor.catchOnly[ConditionalCheckFailedException] {
          client.putItem(javaPutRequest(req))
        }
      case Get(req) =>
        client.getItem(req)
      case Delete(req) =>
        client.deleteItem(javaDeleteRequest(req))
      case ConditionalDelete(req) =>
        Xor.catchOnly[ConditionalCheckFailedException] {
          client.deleteItem(javaDeleteRequest(req))
        }
      case Scan(req) =>
        client.scan(req)
      case Query(req) =>
        client.query(req)
      case BatchWrite(req) =>
        client.batchWriteItem(req)
      case BatchGet(req) =>
        client.batchGetItem(req)
      case Update(req) =>
        client.updateItem(javaUpdateRequest(req))
      case ConditionalUpdate(req) =>
        Xor.catchOnly[ConditionalCheckFailedException] {
          client.updateItem(javaUpdateRequest(req))
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
        futureOf(client.putItemAsync, javaPutRequest(req))
      case ConditionalPut(req) =>
        futureOf(client.putItemAsync, javaPutRequest(req))
          .map(Xor.right[ConditionalCheckFailedException, PutItemResult])
          .recover {
            case e: ConditionalCheckFailedException => Xor.left(e)
          }
      case Get(req) =>
        futureOf(client.getItemAsync, req)
      case Delete(req) =>
        futureOf(client.deleteItemAsync, javaDeleteRequest(req))
      case ConditionalDelete(req) =>
        futureOf(client.deleteItemAsync, javaDeleteRequest(req))
          .map(Xor.right[ConditionalCheckFailedException, DeleteItemResult])
          .recover { case e: ConditionalCheckFailedException => Xor.left(e) }
      case Scan(req) =>
        futureOf(client.scanAsync, req)
      case Query(req) =>
        futureOf(client.queryAsync, req)
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        futureOf(client.batchWriteItemAsync(_: BatchWriteItemRequest, _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]), req)
      case BatchGet(req) =>
        futureOf(client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]), req)
      case Update(req) =>
        futureOf(client.updateItemAsync, javaUpdateRequest(req))
      case ConditionalUpdate(req) =>
        futureOf(client.updateItemAsync, javaUpdateRequest(req))
          .map(Xor.right[ConditionalCheckFailedException, UpdateItemResult])
          .recover {
            case e: ConditionalCheckFailedException => Xor.left(e)
          }
    }
  }
}

