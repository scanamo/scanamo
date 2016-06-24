package com.gu.scanamo.ops

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.request.{ScanamoDeleteRequest, ScanamoPutRequest, ScanamoUpdateRequest}

sealed trait ScanamoOpsA[A] extends Product with Serializable
final case class Put(req: ScanamoPutRequest) extends ScanamoOpsA[PutItemResult]
final case class ConditionalPut(req: ScanamoPutRequest) extends ScanamoOpsA[Xor[ConditionalCheckFailedException, PutItemResult]]
final case class Get(req: GetItemRequest) extends ScanamoOpsA[GetItemResult]
final case class Delete(req: ScanamoDeleteRequest) extends ScanamoOpsA[DeleteItemResult]
final case class ConditionalDelete(req: ScanamoDeleteRequest) extends ScanamoOpsA[Xor[ConditionalCheckFailedException, DeleteItemResult]]
final case class Scan(req: ScanRequest) extends ScanamoOpsA[ScanResult]
final case class Query(req: QueryRequest) extends ScanamoOpsA[QueryResult]
final case class BatchWrite(req: BatchWriteItemRequest) extends ScanamoOpsA[BatchWriteItemResult]
final case class BatchGet(req: BatchGetItemRequest) extends ScanamoOpsA[BatchGetItemResult]
final case class Update(req: ScanamoUpdateRequest) extends ScanamoOpsA[UpdateItemResult]
final case class ConditionalUpdate(req: ScanamoUpdateRequest) extends ScanamoOpsA[Xor[ConditionalCheckFailedException, UpdateItemResult]]

object ScanamoOps {

  import cats.free.Free.liftF

  def put(req: ScanamoPutRequest): ScanamoOps[PutItemResult] = liftF[ScanamoOpsA, PutItemResult](Put(req))
  def conditionalPut(req: ScanamoPutRequest): ScanamoOps[Xor[ConditionalCheckFailedException, PutItemResult]] =
    liftF[ScanamoOpsA, Xor[ConditionalCheckFailedException, PutItemResult]](ConditionalPut(req))
  def get(req: GetItemRequest): ScanamoOps[GetItemResult] = liftF[ScanamoOpsA, GetItemResult](Get(req))
  def delete(req: ScanamoDeleteRequest): ScanamoOps[DeleteItemResult] = liftF[ScanamoOpsA, DeleteItemResult](Delete(req))
  def conditionalDelete(req: ScanamoDeleteRequest): ScanamoOps[Xor[ConditionalCheckFailedException, DeleteItemResult]] =
    liftF[ScanamoOpsA, Xor[ConditionalCheckFailedException, DeleteItemResult]](ConditionalDelete(req))
  def scan(req: ScanRequest): ScanamoOps[ScanResult] = liftF[ScanamoOpsA, ScanResult](Scan(req))
  def query(req: QueryRequest): ScanamoOps[QueryResult] = liftF[ScanamoOpsA, QueryResult](Query(req))
  def batchWrite(req: BatchWriteItemRequest): ScanamoOps[BatchWriteItemResult] =
    liftF[ScanamoOpsA, BatchWriteItemResult](BatchWrite(req))
  def batchGet(req: BatchGetItemRequest): ScanamoOps[BatchGetItemResult] =
    liftF[ScanamoOpsA, BatchGetItemResult](BatchGet(req))
  def update(req: ScanamoUpdateRequest): ScanamoOps[UpdateItemResult] =
    liftF[ScanamoOpsA, UpdateItemResult](Update(req))
  def conditionalUpdate(req: ScanamoUpdateRequest): ScanamoOps[Xor[ConditionalCheckFailedException, UpdateItemResult]] =
    liftF[ScanamoOpsA, Xor[ConditionalCheckFailedException, UpdateItemResult]](ConditionalUpdate(req))
}