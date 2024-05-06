/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo.ops

import software.amazon.awssdk.services.dynamodb.model._
import org.scanamo.request._

sealed trait ScanamoOpsA[A] extends Product with Serializable
final case class Put(req: ScanamoPutRequest) extends ScanamoOpsA[PutItemResponse]
final case class ConditionalPut(req: ScanamoPutRequest)
    extends ScanamoOpsA[Either[ConditionalCheckFailedException, PutItemResponse]]
final case class Get(req: GetItemRequest) extends ScanamoOpsA[GetItemResponse]
final case class Delete(req: ScanamoDeleteRequest) extends ScanamoOpsA[DeleteItemResponse]
final case class ConditionalDelete(req: ScanamoDeleteRequest)
    extends ScanamoOpsA[Either[ConditionalCheckFailedException, DeleteItemResponse]]
final case class Scan(req: ScanamoScanRequest) extends ScanamoOpsA[ScanResponse]
final case class Query(req: ScanamoQueryRequest) extends ScanamoOpsA[QueryResponse]
final case class BatchWrite(req: BatchWriteItemRequest) extends ScanamoOpsA[BatchWriteItemResponse]
final case class BatchGet(req: BatchGetItemRequest) extends ScanamoOpsA[BatchGetItemResponse]
final case class Update(req: ScanamoUpdateRequest) extends ScanamoOpsA[UpdateItemResponse]
final case class ConditionalUpdate(req: ScanamoUpdateRequest)
    extends ScanamoOpsA[Either[ConditionalCheckFailedException, UpdateItemResponse]]
final case class TransactWriteAll(req: ScanamoTransactWriteRequest) extends ScanamoOpsA[Either[TransactionCanceledException, TransactWriteItemsResponse]]

object ScanamoOps {
  import cats.free.Free.liftF

  def put(req: ScanamoPutRequest): ScanamoOps[PutItemResponse] = liftF[ScanamoOpsA, PutItemResponse](Put(req))
  def conditionalPut(req: ScanamoPutRequest): ScanamoOps[Either[ConditionalCheckFailedException, PutItemResponse]] =
    liftF[ScanamoOpsA, Either[ConditionalCheckFailedException, PutItemResponse]](ConditionalPut(req))
  def get(req: GetItemRequest): ScanamoOps[GetItemResponse] = liftF[ScanamoOpsA, GetItemResponse](Get(req))
  def delete(req: ScanamoDeleteRequest): ScanamoOps[DeleteItemResponse] =
    liftF[ScanamoOpsA, DeleteItemResponse](Delete(req))
  def conditionalDelete(
    req: ScanamoDeleteRequest
  ): ScanamoOps[Either[ConditionalCheckFailedException, DeleteItemResponse]] =
    liftF[ScanamoOpsA, Either[ConditionalCheckFailedException, DeleteItemResponse]](ConditionalDelete(req))
  def scan(req: ScanamoScanRequest): ScanamoOps[ScanResponse] = liftF[ScanamoOpsA, ScanResponse](Scan(req))
  def query(req: ScanamoQueryRequest): ScanamoOps[QueryResponse] = liftF[ScanamoOpsA, QueryResponse](Query(req))
  def batchWrite(req: BatchWriteItemRequest): ScanamoOps[BatchWriteItemResponse] =
    liftF[ScanamoOpsA, BatchWriteItemResponse](BatchWrite(req))
  def batchGet(req: BatchGetItemRequest): ScanamoOps[BatchGetItemResponse] =
    liftF[ScanamoOpsA, BatchGetItemResponse](BatchGet(req))
  def update(req: ScanamoUpdateRequest): ScanamoOps[UpdateItemResponse] =
    liftF[ScanamoOpsA, UpdateItemResponse](Update(req))
  def conditionalUpdate(
    req: ScanamoUpdateRequest
  ): ScanamoOps[Either[ConditionalCheckFailedException, UpdateItemResponse]] =
    liftF[ScanamoOpsA, Either[ConditionalCheckFailedException, UpdateItemResponse]](ConditionalUpdate(req))
  def transactWriteAll(
    req: ScanamoTransactWriteRequest
  ): ScanamoOps[Either[TransactionCanceledException, TransactWriteItemsResponse]] =
    liftF[ScanamoOpsA, Either[TransactionCanceledException, TransactWriteItemsResponse]](TransactWriteAll(req))

}
