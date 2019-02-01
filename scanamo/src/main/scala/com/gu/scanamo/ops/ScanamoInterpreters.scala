package org.scanamo.ops

import java.util.concurrent.{CompletableFuture, ExecutionException}

import cats._
import cats.data.NonEmptyList
import cats.syntax.either._
import org.scanamo.request._
import software.amazon.awssdk.services.dynamodb.model._
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbClient}
import scala.concurrent.Future
import scala.util.Try

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
  def id(client: DynamoDbClient) = new (ScanamoOpsA ~> Id) {
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
  def future(client: DynamoDbAsyncClient) = new (ScanamoOpsA ~> Future) {

    private def futureOf[T]
    (
      req: CompletableFuture[T],
    ): Future[T] = {
      Future.fromTry(Try(req.get()))
    }

    private def futureAsEither[T, E <: DynamoDbException](req: CompletableFuture[T])(f : Throwable => Option[E]) : Future[Either[E, T]] = {
      Future.fromTry {
        Try(req.get()).map(Either.right[E, T]).recover {
          case ec : ExecutionException if f(ec.getCause).isDefined =>
            val a = f(ec.getCause).get
            Either.left(a)
        }
      }
    }

    override def apply[A](op: ScanamoOpsA[A]): Future[A] = op match {
      case Put(req) =>
        futureOf(client.putItem(JavaRequests.put(req)))
      case ConditionalPut(req) =>
        futureAsEither[PutItemResponse, ConditionalCheckFailedException](client.putItem(JavaRequests.put(req))) {
          case e: ConditionalCheckFailedException => Some(e)
          case _ => None
        }
      case Get(req) =>
        futureOf(client.getItem(req))
      case Delete(req) =>
        futureOf(client.deleteItem(JavaRequests.delete(req)))
      case ConditionalDelete(req) =>
        futureAsEither[DeleteItemResponse, ConditionalCheckFailedException](client.deleteItem(JavaRequests.delete(req))) {
          case e: ConditionalCheckFailedException => Some(e)
          case _ => None
        }
      case Scan(req) =>
        futureOf(client.scan(JavaRequests.scan(req)))
      case Query(req) =>
        futureOf(client.query(JavaRequests.query(req)))
      // Overloading means we need explicit parameter types here
      case BatchWrite(req) =>
        futureOf(client.batchWriteItem(req))
      case BatchGet(req) =>
        futureOf(client.batchGetItem(req))
      case Update(req) =>
        futureOf(client.updateItem(JavaRequests.update(req)))
      case ConditionalUpdate(req) =>
        futureAsEither[UpdateItemResponse, ConditionalCheckFailedException](client.updateItem(JavaRequests.update(req))){
          case e: ConditionalCheckFailedException => Some(e)
          case _ => None
        }
    }
  }
}

private[ops] object JavaRequests {

  import collection.JavaConverters._

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
        queryRefinement(_.options.exclusiveStartKey)((r, k) => r.exclusiveStartKey(k)),
        queryRefinement(_.options.filter)((r, f) => {
          val requestCondition = f.apply(None)
          val filteredRequest = r
            .filterExpression(requestCondition.expression)
            .expressionAttributeNames(requestCondition.attributeNames.asJava)
          requestCondition.attributeValues
            .fold(filteredRequest)(avs => filteredRequest.expressionAttributeValues(avs.asJava))
        })
      )
      .reduceLeft(_.compose(_))(
        ScanRequest.builder().tableName(req.tableName).consistentRead(req.options.consistent)
      )
      .build()
  }

  def query(req: ScanamoQueryRequest): QueryRequest = {
    def queryRefinement[T](
                            o: ScanamoQueryRequest => Option[T]
                          )(rt: (QueryRequest.Builder, T) => QueryRequest.Builder): QueryRequest.Builder => QueryRequest.Builder = { qr =>
      o(req).foldLeft(qr)(rt)
    }

    NonEmptyList
      .of(
        queryRefinement(_.index)(_.indexName(_)),
        queryRefinement(_.options.limit)(_.limit(_)),
        queryRefinement(_.options.exclusiveStartKey)((r, k) => r.exclusiveStartKey(k)),
        queryRefinement(_.options.filter)((r, f) => {
          val requestCondition = f.apply(None)

          val curReq = r.build()

          r.filterExpression(requestCondition.expression)
            .expressionAttributeNames(
              (curReq.expressionAttributeNames.asScala ++ requestCondition.attributeNames).asJava
            )
            .expressionAttributeValues(
              (curReq.expressionAttributeValues.asScala ++ requestCondition.attributeValues.getOrElse(Map.empty)).asJava
            )
        })
      )
      .reduceLeft(_.compose(_))(
        req.query(QueryRequest.builder().tableName(req.tableName).consistentRead(req.options.consistent))
      ).build()
  }

  def put(req: ScanamoPutRequest): PutItemRequest =
    req.condition.foldLeft(
      PutItemRequest.builder().tableName(req.tableName)
        .item(req.item.m())
        .returnValues(ReturnValue.ALL_OLD)
    )(
      (r, c) =>
        c.attributeValues.foldLeft(
          r.conditionExpression(c.expression).expressionAttributeNames(c.attributeNames.asJava)
        )((cond, values) => cond.expressionAttributeValues(values.asJava))
    ).build()

  def delete(req: ScanamoDeleteRequest): DeleteItemRequest = {
    req.condition.foldLeft(
      DeleteItemRequest.builder().tableName(req.tableName)
        .key(req.key.asJava)
        .returnValues(ReturnValue.ALL_OLD)
    )(
      (r, c) =>
        c.attributeValues.foldLeft(
          r.conditionExpression(c.expression).expressionAttributeNames(c.attributeNames.asJava)
        )((cond, values) => cond.expressionAttributeValues(values.asJava))
    ).build()
  }

  def update(req: ScanamoUpdateRequest): UpdateItemRequest = {
    val reqWithoutValues = req.condition.foldLeft(
      UpdateItemRequest.builder()
        .tableName(req.tableName)
        .key(req.key.asJava)
        .updateExpression(req.updateExpression)
        .expressionAttributeNames(req.attributeNames.asJava)
        .returnValues(ReturnValue.ALL_NEW)
    )(
      (r, c) =>
        c.attributeValues.foldLeft(
          r.conditionExpression(c.expression)
            .expressionAttributeNames((c.attributeNames ++ req.attributeNames).asJava)
        )((cond, values) => cond.expressionAttributeValues((values ++ req.attributeValues).asJava))
    )

    val attributeValues = req.condition.flatMap(_.attributeValues).foldLeft(req.attributeValues)(_ ++ _)
    val out = {
      if (attributeValues.isEmpty) reqWithoutValues
      else reqWithoutValues.expressionAttributeValues(attributeValues.asJava)
    }
    out.build()
  }
}
