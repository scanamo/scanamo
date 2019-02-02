package com.gu.scanamo.ops

import com.amazonaws.services.dynamodbv2.{model => v1}
import software.amazon.awssdk.awscore.AwsResponse
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.http.{AbortableInputStream, SdkHttpResponse}
import software.amazon.awssdk.services.dynamodb.model._
import software.amazon.awssdk.utils.StringInputStream

import scala.collection.JavaConverters._

object aws {
  implicit final class UpdateOps(req: UpdateItemRequest) {
    def legacy: v1.UpdateItemRequest =
      new v1.UpdateItemRequest(
        req.tableName(),
        req.key,
        req.attributeUpdates().asScala.mapValues(v1AttrValueUpdate).asJava,
        req.returnValuesAsString()
      )
  }

  implicit final def v1AttrValueUpdate(atr: AttributeValueUpdate): v1.AttributeValueUpdate = ???
  implicit final def v2AttrValueUpdate(atr: v1.AttributeValueUpdate): AttributeValueUpdate = ???

  implicit final class UpdateRespOps(req: v1.UpdateItemResult) {
    def fromLegacy: UpdateItemResponse =
      UpdateItemResponse
        .builder()
        .attributes(req.getAttributes)
        .consumedCapacity(req.getConsumedCapacity.fromLegacy)
        .itemCollectionMetrics(req.getItemCollectionMetrics)
        .build()
  }

  implicit final def v1AttributesList(
    v2: java.util.Map[String, AttributeValue]
  ): java.util.Map[String, v1.AttributeValue] =
    v2.asScala.mapValues(_.asLegacy).asJava

  implicit final def v2AttributesList(
    v1List: java.util.Map[String, v1.AttributeValue]
  ): java.util.Map[String, AttributeValue] =
    v1List.asScala.mapValues(_.fromLegacy).asJava

  implicit final class PutOps(req: PutItemRequest) {
    def legacy: v1.PutItemRequest =
      new v1.PutItemRequest(req.tableName, req.item, req.returnValuesAsString())
  }

  implicit final class PutResponseOps(req: v1.PutItemResult) {
    def fromLegacy: PutItemResponse =
      PutItemResponse
        .builder()
        .attributes(req.getAttributes)
        .consumedCapacity(req.getConsumedCapacity.fromLegacy)
        .itemCollectionMetrics(req.getItemCollectionMetrics)
        .build()
  }

  implicit final class GetOps(req: GetItemRequest) {
    def legacy: v1.GetItemRequest =
      new v1.GetItemRequest(req.tableName, req.key, req.consistentRead)
  }

  implicit final class GetResponseOps(req: v1.GetItemResult) {
    def fromLegacy: GetItemResponse =
      GetItemResponse.builder
        .consumedCapacity(req.getConsumedCapacity.fromLegacy)
        .item(req.getItem)
  }

  implicit final class DeleteOps(req: DeleteItemRequest) {
    def legacy: v1.DeleteItemRequest =
      new v1.DeleteItemRequest(req.tableName, req.key, req.returnValuesAsString)
  }

  implicit final class DeleteResponseOps(req: v1.DeleteItemResult) {
    def fromLegacy: DeleteItemResponse =
      DeleteItemResponse
        .builder()
        .attributes(req.getAttributes)
        .consumedCapacity(req.getConsumedCapacity.fromLegacy)
        .itemCollectionMetrics(req.getItemCollectionMetrics)
  }

  implicit def complete[B <: AwsResponse.Builder, T <: AwsResponse](b: AwsResponse.Builder): T =
    b.build().asInstanceOf[T]

  implicit final class ScanOps(req: ScanRequest) {
    def legacy: v1.ScanRequest =
      new v1.ScanRequest(req.tableName())
  }

  implicit final class ScanResponseOps(req: v1.ScanResult) {
    def fromLegacy: ScanResponse =
      ScanResponse
        .builder()
        .consumedCapacity(req.getConsumedCapacity.fromLegacy)
        .count(req.getCount)
        .scannedCount(req.getScannedCount)
        .items(req.getItems.asScala.map(i => i: java.util.Map[String, AttributeValue]).asJava)
        .lastEvaluatedKey(req.getLastEvaluatedKey)
  }

  implicit final class BatchWriteItemOps(req: BatchWriteItemRequest) {
    def legacy: v1.BatchWriteItemRequest =
      //        new v1.BatchWriteItemRequest(req.requestItems().asScala.mapValues(_.asScala.map(_.le)))
      ???
  }

  implicit final def legacyItemCollMetrics(v2Metrics: ItemCollectionMetrics): v1.ItemCollectionMetrics = {
    val m = new v1.ItemCollectionMetrics()
    m.withItemCollectionKey(v2Metrics.itemCollectionKey().asScala.mapValues(_.asLegacy).asJava)
    m.withSizeEstimateRangeGB(v2Metrics.sizeEstimateRangeGB())
    m
  }

  implicit final def v2ItemCollMetrics(v1Metrics: v1.ItemCollectionMetrics): ItemCollectionMetrics =
    ItemCollectionMetrics
      .builder()
      .itemCollectionKey(v1Metrics.getItemCollectionKey.asScala.mapValues(_.fromLegacy).asJava)
      .sizeEstimateRangeGB(v1Metrics.getSizeEstimateRangeGB)
      .build()

  implicit final def v1WriteRequest(req: WriteRequest): v1.WriteRequest =
    //      new v1.WriteRequest(req.putRequest(), req.deleteRequest())
    ???

  implicit final class BatchWriteItemRespOps(req: v1.BatchWriteItemResult) {
    def fromLegacy: BatchWriteItemResponse = ???
  }

  implicit def v1PutRequest(v2: v1.PutItemRequest): PutItemRequest =
    PutItemRequest
      .builder()
      .tableName(v2.getTableName)
      .conditionalOperator(v2.getConditionalOperator)
      .conditionExpression(v2.getConditionExpression)
      .returnValues(v2.getReturnValues)
      .item(v2.getItem)
      .returnConsumedCapacity(v2.getReturnConsumedCapacity)
      .build()

  implicit final class QueryOps(req: QueryRequest) {
    def legacy: v1.QueryRequest = ???
  }

  implicit final class QueryRespOps(req: v1.QueryResult) {
    def fromLegacy: QueryResponse =
      ???
  }

  implicit final class BatchGetItemOps(req: BatchGetItemRequest) {
    def legacy: v1.BatchGetItemRequest =
      //        new v1.BatchGetItemRequest(req.requestItems(), req.returnConsumedCapacity())
      ???
  }

  implicit final class BatchGetItemRespOps(req: v1.BatchGetItemResult) {
    def fromLegacy: BatchGetItemResponse = {

      req.getUnprocessedKeys.asScala.mapValues { k =>
        KeysAndAttributes
          .builder()
          .projectionExpression(k.getProjectionExpression)
          .keys(k.getKeys.asScala.map(_.asScala.mapValues(_.fromLegacy).asJava).asJava)
          .attributesToGet(k.getAttributesToGet)
          .consistentRead(k.getConsistentRead)
          .expressionAttributeNames(k.getExpressionAttributeNames)
      }

      val a = BatchGetItemResponse
        .builder()
        .responses(req.getResponses)
        .unprocessedKeys(req.getUnprocessedKeys.asScala.mapValues(_.fromLegacy).asJava)
        .consumedCapacity(req.getConsumedCapacity.asScala.map(_.fromLegacy).asJava)
      a
    }
  }

  implicit final class KeyAndAttributesOps(req: v1.KeysAndAttributes) {
    def fromLegacy: KeysAndAttributes =
      //        KeysAndAttributes.builder()
      //          .attributesToGet(req.getAttributesToGet)
      //          .consistentRead(req.getConsistentRead)
      //          .projectionExpression(req.getProjectionExpression)
      //          .expressionAttributeNames(req.getExpressionAttributeNames)
      //          .keys(req.getKeys.asScala.map(_.asScala.mapValues(_.fromLegacy).asJava).asJava)
      ???
  }

  implicit final class ConsumedCapacityOps(req: v1.ConsumedCapacity) {
    implicit def fromLegacy: ConsumedCapacity =
      ConsumedCapacity
        .builder()
        .tableName(req.getTableName)
        .table(req.getTable.fromLegacy)
        .readCapacityUnits(req.getCapacityUnits)
        .writeCapacityUnits(req.getWriteCapacityUnits)
        .capacityUnits(req.getCapacityUnits)
        .localSecondaryIndexes(req.getLocalSecondaryIndexes.asScala.mapValues(_.fromLegacy).asJava)
        .globalSecondaryIndexes(req.getGlobalSecondaryIndexes.asScala.mapValues(_.fromLegacy).asJava)
        .build()
  }

  implicit final class CapacityOps(cap: v1.Capacity) {
    def fromLegacy: Capacity =
      Capacity
        .builder()
        .capacityUnits(cap.getCapacityUnits)
        .readCapacityUnits(cap.getReadCapacityUnits)
        .writeCapacityUnits(cap.getWriteCapacityUnits)
        .build()
  }

  implicit final class AttributeValueOps(req: v1.AttributeValue) {
    def fromLegacy: AttributeValue =
      AttributeValue
        .builder()
        .b(SdkBytes.fromByteBuffer(req.getB))
        .bool(req.getBOOL)
        .bs(req.getBS.asScala.map(SdkBytes.fromByteBuffer).asJava)
        .l(req.getL.asScala.map(i => i.fromLegacy).toList.asJava)
        .m(req.getM.asScala.mapValues(_.fromLegacy).asJava)
        .s(req.getS)
        .ss(req.getSS)
        .n(req.getN)
        .ns(req.getNS)
        .nul(req.getNULL)
        .build()

  }

  implicit final class AttributeValue2Ops(req: AttributeValue) {
    def asLegacy: v1.AttributeValue = {
      val v = new v1.AttributeValue()
      v.withB(req.b().asByteBuffer())
      v.withBOOL(req.bool())
      v.withBS(req.bs().asScala.map(_.asByteBuffer()).asJava)
      v.withL(req.l().asScala.map(_.asLegacy).asJava)
      v.withM(req.m().asScala.mapValues(_.asLegacy).asJava)
      v.withN(req.n())
      v.withNS(req.ns())
      v.withNULL(req.nul())
      v.withS(req.s())
      v.withSS(req.ss())
    }
  }

  implicit final class ExcpetoOps(req: v1.ConditionalCheckFailedException) {
    def fromLegacy: ConditionalCheckFailedException = {

      val sdkHttpResponse = SdkHttpResponse
        .builder()
        .statusText("")
        .statusCode(req.getStatusCode)
        .headers(req.getHttpHeaders.asScala.mapValues(List.apply(_).asJava).asJava)
        .content(AbortableInputStream.create(new StringInputStream(req.getRawResponseContent)))
        .build()

      val details = AwsErrorDetails
        .builder()
        .serviceName(req.getServiceName)
        .errorCode(req.getErrorCode)
        .errorMessage(req.getErrorMessage)
        .rawResponse(SdkBytes.fromByteArray(req.getRawResponse))
        .sdkHttpResponse(sdkHttpResponse)
        .build()

      ConditionalCheckFailedException
        .builder()
        .cause(req.getCause)
        .message(req.getMessage)
        .requestId(req.getRequestId)
        .statusCode(req.getStatusCode)
        .awsErrorDetails(details)
        .build()
        .asInstanceOf[ConditionalCheckFailedException]
    }
  }
}
