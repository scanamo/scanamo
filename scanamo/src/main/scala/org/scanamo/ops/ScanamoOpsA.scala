package org.scanamo.ops

import cats.free.Free
import cats.{Functor, Monad, MonadError}
import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.ops.ScanamoOpsA.ScanamoResult
import org.scanamo.request._

sealed trait ScanamoOpsA[+A] extends Product with Serializable
object ScanamoOpsA {
  type ScanamoResult[A] = Either[AmazonDynamoDBException, A]
}
final case class Put[A](req: ScanamoPutRequest, res: ScanamoResult[PutItemResult] => A) extends ScanamoOpsA[A]
final case class Get[A](req: GetItemRequest, res: ScanamoResult[GetItemResult] => A) extends ScanamoOpsA[A]
final case class Delete[A](req: ScanamoDeleteRequest, res: ScanamoResult[DeleteItemResult] => A) extends ScanamoOpsA[A]
final case class Scan[A](req: ScanamoScanRequest, res: ScanResult => A) extends ScanamoOpsA[A]
final case class Query[A](req: ScanamoQueryRequest, res: QueryResult => A) extends ScanamoOpsA[A]
final case class BatchWrite[A](req: BatchWriteItemRequest, res: BatchWriteItemResult => A) extends ScanamoOpsA[A]
final case class BatchGet[A](req: BatchGetItemRequest, res: BatchGetItemResult => A) extends ScanamoOpsA[A]
final case class Update[A](req: ScanamoUpdateRequest, res: ScanamoResult[UpdateItemResult] => A) extends ScanamoOpsA[A]
final case class Fail(t: Throwable) extends ScanamoOpsA[Nothing]

object ScanamoOps {

  import cats.free.Free.liftF

  def put(req: ScanamoPutRequest): ScanamoOps[ScanamoResult[PutItemResult]] =
    liftF[ScanamoOpsA, ScanamoResult[PutItemResult]](Put(req, identity))
  def get(req: GetItemRequest): ScanamoOps[ScanamoResult[GetItemResult]] =
    liftF[ScanamoOpsA, ScanamoResult[GetItemResult]](Get(req, identity))
  def delete(
    req: ScanamoDeleteRequest
  ): ScanamoOps[ScanamoResult[DeleteItemResult]] =
    liftF[ScanamoOpsA, ScanamoResult[DeleteItemResult]](Delete(req, identity))
  def scan(req: ScanamoScanRequest): ScanamoOps[ScanResult] = liftF[ScanamoOpsA, ScanResult](Scan(req, identity))
  def query(req: ScanamoQueryRequest): ScanamoOps[QueryResult] = liftF[ScanamoOpsA, QueryResult](Query(req, identity))
  def batchWrite(req: BatchWriteItemRequest): ScanamoOps[BatchWriteItemResult] =
    liftF[ScanamoOpsA, BatchWriteItemResult](BatchWrite(req, identity))
  def batchGet(req: BatchGetItemRequest): ScanamoOps[BatchGetItemResult] =
    liftF[ScanamoOpsA, BatchGetItemResult](BatchGet(req, identity))
  def update(
    req: ScanamoUpdateRequest
  ): ScanamoOps[ScanamoResult[UpdateItemResult]] =
    liftF[ScanamoOpsA, ScanamoResult[UpdateItemResult]](Update(req, identity))
  def fail(t: Throwable): Free[ScanamoOpsA, Nothing] = liftF[ScanamoOpsA, Nothing](Fail(t))

  implicit val functionScanamoOps = new Functor[ScanamoOpsA] {
    override def map[A, B](fa: ScanamoOpsA[A])(f: A => B): ScanamoOpsA[B] = fa match {
      case Put(req, res) => Put(req, res.andThen(f))
      case Get(req, res) => Get(req, res.andThen(f))
      case Delete(req, res) => Delete(req, res.andThen(f))
      case Scan(req, res) => Scan(req, res.andThen(f))
      case Query(req, res) => Query(req, res.andThen(f))
      case BatchWrite(req, res) => BatchWrite(req, res.andThen(f))
      case BatchGet(req, res) => BatchGet(req, res.andThen(f))
      case Update(req, res) => Update(req, res.andThen(f))
      case fail: Fail => fail
    }
  }


  implicit val monadErrorScanamoOps = new MonadError[ScanamoOps, Throwable] {
    def raiseError[A](e: Throwable): ScanamoOps[A] = liftF[ScanamoOpsA, A](Fail(e))

    override def handleErrorWith[A](fa: ScanamoOps[A])(f: Throwable => ScanamoOps[A]): ScanamoOps[A] =
      fa.resume match {
        case Left(Fail(t)) => f(t)
        case Left(_)  => f(new Throwable("Unexpected exception"))
        case Right(a) => Monad[ScanamoOps].pure(a)
    }

    override def flatMap[A, B](fa: ScanamoOps[A])(f: A => ScanamoOps[B]): ScanamoOps[B] = fa.flatMap(f)
    override def tailRecM[A, B](a: A)(f: A => ScanamoOps[Either[A, B]]): ScanamoOps[B] =
      f(a).flatMap(_ match {
        case Left(a1) => tailRecM(a1)(f)
        case Right(b) => pure(b)

      })
    override def pure[A](a: A): ScanamoOps[A] = Free.pure(a)
  }
}
