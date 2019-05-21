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
import org.scanamo.{DynamoObject, DynamoValue}

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
        client.query(JavaRequests.query(req))
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
        def onError(exception: Exception): Unit = { p.complete(Failure(exception)); () }
        def onSuccess(request: X, result: T): Unit = { p.complete(Success(result)); () }
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
        futureOf(client.queryAsync, JavaRequests.query(req))
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        futureOf(
          client.batchWriteItemAsync(
            _: BatchWriteItemRequest,
            _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]
          ),
          req
        )
      case BatchGet(req) =>
        futureOf(
          client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]),
          req
        )
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
      val attributeValues = for {
        queryValues <- queryCondition.dynamoValues orElse Some(DynamoObject.empty)
        filterValues <- condition.dynamoValues orElse Some(DynamoObject.empty)
      } yield queryValues <> filterValues

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
