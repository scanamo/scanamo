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

import org.scanamo.ops.ScanamoOps.Results.*
import org.scanamo.request.*
import software.amazon.awssdk.services.dynamodb.model.*

sealed trait ScanamoOpsA[A] extends Product with Serializable
final case class Put(req: ScanamoPutRequest) extends ScanamoOpsA[PutItemResponse]
final case class ConditionalPut(req: ScanamoPutRequest) extends ScanamoOpsA[Conditional[PutItemResponse]]
final case class Get(req: GetItemRequest) extends ScanamoOpsA[GetItemResponse]
final case class Delete(req: ScanamoDeleteRequest) extends ScanamoOpsA[DeleteItemResponse]
final case class ConditionalDelete(req: ScanamoDeleteRequest) extends ScanamoOpsA[Conditional[DeleteItemResponse]]
final case class Scan(req: ScanamoScanRequest) extends ScanamoOpsA[ScanResponse]
final case class Query(req: ScanamoQueryRequest) extends ScanamoOpsA[QueryResponse]
final case class BatchWrite(req: BatchWriteItemRequest) extends ScanamoOpsA[BatchWriteItemResponse]
final case class BatchGet(req: BatchGetItemRequest) extends ScanamoOpsA[BatchGetItemResponse]
final case class Update(req: ScanamoUpdateRequest) extends ScanamoOpsA[UpdateItemResponse]
final case class ConditionalUpdate(req: ScanamoUpdateRequest) extends ScanamoOpsA[Conditional[UpdateItemResponse]]
final case class TransactWriteAll(req: ScanamoTransactWriteRequest)
    extends ScanamoOpsA[Transact[TransactWriteItemsResponse]]
final case class UpdateTimeToLive(req: ScanamoUpdateTimeToLiveRequest) extends ScanamoOpsA[UpdateTimeToLiveResponse]

object ScanamoOps {
  import cats.free.Free.liftF

  object Results {
    type Conditional[T] = Either[ConditionalCheckFailedException, T]
    type Transact[T] = Either[TransactionCanceledException, T]
  }

  private def lF[Result](req: ScanamoOpsA[Result]): ScanamoOps[Result] = liftF[ScanamoOpsA, Result](req)

  def put(req: ScanamoPutRequest): ScanamoOps[PutItemResponse] = lF(Put(req))
  def conditionalPut(req: ScanamoPutRequest): ScanamoOps[Conditional[PutItemResponse]] = lF(ConditionalPut(req))
  def get(req: GetItemRequest): ScanamoOps[GetItemResponse] = lF(Get(req))
  def delete(req: ScanamoDeleteRequest): ScanamoOps[DeleteItemResponse] = lF(Delete(req))
  def conditionalDelete(req: ScanamoDeleteRequest): ScanamoOps[Conditional[DeleteItemResponse]] =
    lF(ConditionalDelete(req))
  def scan(req: ScanamoScanRequest): ScanamoOps[ScanResponse] = lF(Scan(req))
  def query(req: ScanamoQueryRequest): ScanamoOps[QueryResponse] = lF(Query(req))
  def batchWrite(req: BatchWriteItemRequest): ScanamoOps[BatchWriteItemResponse] = lF(BatchWrite(req))
  def batchGet(req: BatchGetItemRequest): ScanamoOps[BatchGetItemResponse] = lF(BatchGet(req))
  def update(req: ScanamoUpdateRequest): ScanamoOps[UpdateItemResponse] = lF(Update(req))
  def conditionalUpdate(req: ScanamoUpdateRequest): ScanamoOps[Conditional[UpdateItemResponse]] = lF(
    ConditionalUpdate(req)
  )
  def transactWriteAll(req: ScanamoTransactWriteRequest): ScanamoOps[Transact[TransactWriteItemsResponse]] = lF(
    TransactWriteAll(req)
  )
  def updateTimeToLiveRequest(req: ScanamoUpdateTimeToLiveRequest): ScanamoOps[UpdateTimeToLiveResponse] = lF(
    UpdateTimeToLive(req)
  )

}
