package org.scanamo.ops

import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.ops.ScanamoOpsA.ScanamoResult
import org.scanamo.request._

sealed trait ScanamoOpsA[A] extends Product with Serializable
object ScanamoOpsA {
  type ScanamoResult[A] = Either[AmazonDynamoDBException, A]
}
final case class Put(req: ScanamoPutRequest) extends ScanamoOpsA[ScanamoResult[PutItemResult]]
final case class Get(req: GetItemRequest) extends ScanamoOpsA[ScanamoResult[GetItemResult]]
final case class Delete(req: ScanamoDeleteRequest)
    extends ScanamoOpsA[ScanamoResult[DeleteItemResult]]
final case class Scan(req: ScanamoScanRequest) extends ScanamoOpsA[ScanResult]
final case class Query(req: ScanamoQueryRequest) extends ScanamoOpsA[QueryResult]
final case class BatchWrite(req: BatchWriteItemRequest) extends ScanamoOpsA[BatchWriteItemResult]
final case class BatchGet(req: BatchGetItemRequest) extends ScanamoOpsA[BatchGetItemResult]
final case class Update(req: ScanamoUpdateRequest) extends ScanamoOpsA[UpdateItemResult]
final case class ConditionalUpdate(req: ScanamoUpdateRequest)
    extends ScanamoOpsA[Either[ConditionalCheckFailedException, UpdateItemResult]]

object ScanamoOps {

  import cats.free.Free.liftF

  def put(req: ScanamoPutRequest): ScanamoOps[ScanamoResult[PutItemResult]] = liftF[ScanamoOpsA, ScanamoResult[PutItemResult]](Put(req))
  def get(req: GetItemRequest): ScanamoOps[ScanamoResult[GetItemResult]] = liftF[ScanamoOpsA, ScanamoResult[GetItemResult]](Get(req))
  def delete(
    req: ScanamoDeleteRequest
  ): ScanamoOps[ScanamoResult[DeleteItemResult]] =
    liftF[ScanamoOpsA,ScanamoResult[DeleteItemResult]](Delete(req))
  def scan(req: ScanamoScanRequest): ScanamoOps[ScanResult] = liftF[ScanamoOpsA, ScanResult](Scan(req))
  def query(req: ScanamoQueryRequest): ScanamoOps[QueryResult] = liftF[ScanamoOpsA, QueryResult](Query(req))
  def batchWrite(req: BatchWriteItemRequest): ScanamoOps[BatchWriteItemResult] =
    liftF[ScanamoOpsA, BatchWriteItemResult](BatchWrite(req))
  def batchGet(req: BatchGetItemRequest): ScanamoOps[BatchGetItemResult] =
    liftF[ScanamoOpsA, BatchGetItemResult](BatchGet(req))
  def update(req: ScanamoUpdateRequest): ScanamoOps[UpdateItemResult] =
    liftF[ScanamoOpsA, UpdateItemResult](Update(req))
  def conditionalUpdate(
    req: ScanamoUpdateRequest
  ): ScanamoOps[Either[ConditionalCheckFailedException, UpdateItemResult]] =
    liftF[ScanamoOpsA, Either[ConditionalCheckFailedException, UpdateItemResult]](ConditionalUpdate(req))
}
