package com.gu.scanamo

import java.util

import cats.Later
import cats.data._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import collection.convert.decorateAsScala._

trait DynamoResultStream[Req, Res] {
  def items(res: Res): java.util.List[java.util.Map[String, AttributeValue]]
  def lastEvaluatedKey(res: Res): java.util.Map[String, AttributeValue]
  def withExclusiveStartKey(req: Req, key: java.util.Map[String, AttributeValue]): Req
  def exec(client: AmazonDynamoDB)(req: Req): Res

  def stream[T](client: AmazonDynamoDB)(req: Req)(implicit f: DynamoFormat[T]): Streaming[ValidatedNel[DynamoReadError, T]] = {
    def streamMore(lastKey: Option[java.util.Map[String, AttributeValue]]): Streaming[ValidatedNel[DynamoReadError, T]] = {
      val queryResult = exec(client)(lastKey.foldLeft(req)(withExclusiveStartKey(_, _)))
      val results = Streaming.fromIterable(items(queryResult).asScala.map(Scanamo.read[T]))
      Option(lastEvaluatedKey(queryResult)).foldLeft(results)((is, k) => is ++ Later(streamMore(Some(k))))
    }
    streamMore(None)
  }
}

object DynamoResultStream {
  object ScanResultStream extends DynamoResultStream[ScanRequest, ScanResult] {
    override def items(res: ScanResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: ScanResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(req: ScanRequest, key: util.Map[String, AttributeValue]): ScanRequest =
      req.withExclusiveStartKey(key)
    override def exec(client: AmazonDynamoDB)(req: ScanRequest): ScanResult = client.scan(req)
  }

  object QueryResultStream extends DynamoResultStream[QueryRequest, QueryResult] {
    override def items(res: QueryResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: QueryResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(req: QueryRequest, key: util.Map[String, AttributeValue]): QueryRequest =
      req.withExclusiveStartKey(key)
    override def exec(client: AmazonDynamoDB)(req: QueryRequest): QueryResult = client.query(req)
  }
}
