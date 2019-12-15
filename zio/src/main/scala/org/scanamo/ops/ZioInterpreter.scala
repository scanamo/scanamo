package org.scanamo.ops

import cats.~>
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.{ Put => _, Delete => _, Update => _, Get => _, _ }
import zio.IO

private[scanamo] class ZioInterpreter(client: AmazonDynamoDBAsync)
    extends (ScanamoOpsA ~> IO[AmazonDynamoDBException, ?]) {
  final private def eff[A <: AmazonWebServiceRequest, B](
    f: (A, AsyncHandler[A, B]) => java.util.concurrent.Future[B],
    req: A
  ): IO[AmazonDynamoDBException, B] =
    IO.effectAsync[AmazonDynamoDBException, B] { cb =>
      val handler = new AsyncHandler[A, B] {
        def onError(exception: Exception): Unit =
          exception match {
            case e: AmazonDynamoDBException => cb(IO.fail(e))
            case t                          => cb(IO.die(t))
          }

        def onSuccess(request: A, result: B): Unit =
          cb(IO.succeed(result))
      }
      val _ = f(req, handler)
    }

  def apply[A](op: ScanamoOpsA[A]): IO[AmazonDynamoDBException, A] = op match {
    case Put(req) =>
      eff(client.putItemAsync, JavaRequests.put(req))
    case ConditionalPut(req) =>
      eff(client.putItemAsync, JavaRequests.put(req))
        .map[Either[ConditionalCheckFailedException, PutItemResult]](Right(_))
        .catchSome {
          case e: ConditionalCheckFailedException => IO.succeed(Left(e))
        }
    case Get(req) =>
      eff(client.getItemAsync, req)
    case Delete(req) =>
      eff(client.deleteItemAsync, JavaRequests.delete(req))
    case ConditionalDelete(req) =>
      eff(client.deleteItemAsync, JavaRequests.delete(req))
        .map[Either[ConditionalCheckFailedException, DeleteItemResult]](Right(_))
        .catchSome {
          case e: ConditionalCheckFailedException => IO.succeed(Left(e))
        }
    case Scan(req) =>
      eff(client.scanAsync, JavaRequests.scan(req))
    case Query(req) =>
      eff(client.queryAsync, JavaRequests.query(req))
    case BatchWrite(req) =>
      eff(
        client.batchWriteItemAsync(
          _: BatchWriteItemRequest,
          _: AsyncHandler[BatchWriteItemRequest, BatchWriteItemResult]
        ),
        req
      )
    case BatchGet(req) =>
      eff(
        client.batchGetItemAsync(_: BatchGetItemRequest, _: AsyncHandler[BatchGetItemRequest, BatchGetItemResult]),
        req
      )
    case Update(req) =>
      eff(client.updateItemAsync, JavaRequests.update(req))
    case ConditionalUpdate(req) =>
      eff(client.updateItemAsync, JavaRequests.update(req))
        .map[Either[ConditionalCheckFailedException, UpdateItemResult]](Right(_))
        .catchSome {
          case e: ConditionalCheckFailedException => IO.succeed(Left(e))
        }
    case TransactPutAll(req) => eff(client.transactWriteItemsAsync _, req)
  }
}
