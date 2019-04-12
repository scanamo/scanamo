package org.scanamo.ops

import cats._
import cats.data.NonEmptyList
import cats.syntax.either._
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBAsync}
import com.amazonaws.services.dynamodbv2.model.{UpdateItemResult, _}
import org.scanamo.request._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
  * Interpreters to take the operations defined with Scanamo and execute them
  * by transforming them from a [[https://typelevel.org/cats/datatypes/freemonad.html Free Monad]]
  * grammar using a [[https://typelevel.org/cats/datatypes/functionk.html FunctionK]]
  */
object ScanamoInterpreters {

  /**
    * Interpret Scanamo operations using blocking requests to DynamoDB with any
    * transport errors or semantic errors within DynamoDB thrown as exceptions.
    *
    * We need to interpret into a type with a type parameter, so cheat by using
    * the [Id Monad](http://typelevel.org/cats/datatypes/id.html) which is just
    * a type alias for the type itself (`type Id[A] = A`).
    */
  def id(client: AmazonDynamoDB) = new (ScanamoOpsA ~> Id) {
    def apply[A](op: ScanamoOpsA[A]): Id[A] = op match {
      case Put(req, res) =>
        res(Either.catchOnly[AmazonDynamoDBException] {
          client.putItem(JavaRequests.put(req))
        })
      case Get(req, res) =>
        res(Either.catchOnly[AmazonDynamoDBException] {
          client.getItem(req)
        })
      case Delete(req, res) =>
        res(Either.catchOnly[AmazonDynamoDBException] {
          client.deleteItem(JavaRequests.delete(req))
        })
      case Scan(req, res) =>
        res(client.scan(JavaRequests.scan(req)))
      case Query(req, res) =>
        res(client.query(JavaRequests.query(req)))
      case BatchWrite(req, res) =>
        res(client.batchWriteItem(req))
      case BatchGet(req, res) =>
        res(client.batchGetItem(req))
      case Update(req, res) =>
        res(Either.catchOnly[AmazonDynamoDBException] {
          client.updateItem(JavaRequests.update(req))
        })
      case Fail(error) => throw error
    }
  }

  /**
    * Interpret Scanamo operations into a `Future` using the AmazonDynamoDBAsync client
    * which doesn't block, using it's own thread pool for I/O requests internally
    */
  def future(client: AmazonDynamoDBAsync)(implicit ec: ExecutionContext) = new (ScanamoOpsA ~> Future) {
    private def futureOf[X <: AmazonWebServiceRequest, T](
      call: (X, AsyncHandler[X, T]) => java.util.concurrent.Future[T],
      req: X
    ): Future[T] = {
      val p = Promise[T]()
      val h = new AsyncHandler[X, T] {
        def onError(exception: Exception) { p.complete(Failure(exception)); () }
        def onSuccess(request: X, result: T) { p.complete(Success(result)); () }
      }
      call(req, h)
      p.future
    }

    override def apply[A](op: ScanamoOpsA[A]): Future[A] = op match {
      case Put(req, res) =>
        futureOf(client.putItemAsync, JavaRequests.put(req))
          .map(x => res(Either.right[AmazonDynamoDBException, PutItemResult](x)))
          .recover {
            case e: AmazonDynamoDBException => res(Either.left(e))
          }
      case Get(req, res) =>
        futureOf(client.getItemAsync, req)
          .map(x => res(Either.right[AmazonDynamoDBException, GetItemResult](x)))
          .recover {
            case e: AmazonDynamoDBException => res(Either.left(e))
          }
      case Delete(req, res) =>
        futureOf(client.deleteItemAsync, JavaRequests.delete(req))
          .map(x => res(Either.right[AmazonDynamoDBException, DeleteItemResult](x)))
          .recover { case e: AmazonDynamoDBException => res(Either.left(e)) }
      case Scan(req, res) =>
        futureOf(client.scanAsync, JavaRequests.scan(req))
          .map(res)
      case Query(req, res) =>
        futureOf(client.queryAsync, JavaRequests.query(req))
          .map(res)
      // Overloading means we need explicit parameter types here
      case BatchWrite(req, res) =>
        futureOf(
          client.batchWriteItemAsync(
            _: BatchWriteItemRequest,
            _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]
          ),
          req
        ).map(res)
      case BatchGet(req, res) =>
        futureOf(
          client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]),
          req
        ).map(res)
      case Update(req, res) =>
        futureOf(client.updateItemAsync, JavaRequests.update(req))
          .map(x => res(Either.right[AmazonDynamoDBException, UpdateItemResult](x)))
          .recover {
            case e: AmazonDynamoDBException => res(Either.left(e))
          }
      case Fail(error) => Future.failed(error)
    }
  }
}

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
          val requestCondition = f.apply(None)
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
          val requestCondition = f.apply(None)
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
