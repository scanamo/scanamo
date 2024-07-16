package org.scanamo.ops

import software.amazon.awssdk.core.async.SdkPublisher
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.paginators.{QueryIterable, QueryPublisher, ScanIterable, ScanPublisher}
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbClient}

import java.util.concurrent.CompletableFuture

object AWSOps {
  sealed case class Op[In <: DynamoDbRequest, Out <: DynamoDbResponse](
    sync: DynamoDbClient => In => Out,
    async: DynamoDbAsyncClient => In => CompletableFuture[Out]
  )

  sealed case class PaginatedOp[In <: DynamoDbRequest, Out <: DynamoDbResponse, Iter <: SdkIterable[Out], Pub <: SdkPublisher[Out]](
    sync: DynamoDbClient => In => Out,
    async: DynamoDbAsyncClient => In => CompletableFuture[Out],
    iter: DynamoDbClient => In => Iter,
    publisher: DynamoDbAsyncClient => In => Pub
  )

  implicit val putItem: Op[PutItemRequest, PutItemResponse] = Op(_.putItem, _.putItem)
  implicit val getItem: Op[GetItemRequest, GetItemResponse] = Op(_.getItem, _.getItem)
  implicit val deleteItem: Op[DeleteItemRequest, DeleteItemResponse] = Op(_.deleteItem, _.deleteItem)
  implicit val scan: PaginatedOp[ScanRequest, ScanResponse, ScanIterable, ScanPublisher] = PaginatedOp(_.scan, _.scan, _.scanPaginator, _.scanPaginator)
  implicit val query: PaginatedOp[QueryRequest, QueryResponse, QueryIterable, QueryPublisher] = PaginatedOp(_.query, _.query, _.queryPaginator, _.queryPaginator)
  implicit val updateItem: Op[UpdateItemRequest, UpdateItemResponse] = Op(_.updateItem, _.updateItem)
  implicit val batchWriteItem: Op[BatchWriteItemRequest, BatchWriteItemResponse] = Op(_.batchWriteItem, _.batchWriteItem)
  implicit val batchGetItem: Op[BatchGetItemRequest, BatchGetItemResponse] = Op(_.batchGetItem, _.batchGetItem)
  implicit val transactWriteAll: Op[TransactWriteItemsRequest, TransactWriteItemsResponse] = Op(_.transactWriteItems, _.transactWriteItems)
}
