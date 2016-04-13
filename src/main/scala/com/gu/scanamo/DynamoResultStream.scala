package com.gu.scanamo

import java.util

import cats.{Later, Monad}
import cats.data._
import cats.free.Free
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._

import collection.convert.decorateAsScala._

trait DynamoResultStream[Req, Res] {
  def items(res: Res): java.util.List[java.util.Map[String, AttributeValue]]
  def lastEvaluatedKey(res: Res): java.util.Map[String, AttributeValue]
  def withExclusiveStartKey(req: Req, key: java.util.Map[String, AttributeValue]): Req
  def exec(req: Req): ScanamoOps[Res]

  def stream[T: DynamoFormat](req: Req): ScanamoOps[Streaming[ValidatedNel[DynamoReadError, T]]] = {

    def streamMore(lastKey: Option[java.util.Map[String, AttributeValue]]): ScanamoOps[Streaming[ValidatedNel[DynamoReadError, T]]] = {
      for {
        queryResult <- exec(lastKey.foldLeft(req)(withExclusiveStartKey(_, _)))
        results = Streaming.fromIterable(items(queryResult).asScala.map(ScanamoFree.read[T]))
        resultStream <-
          Option(lastEvaluatedKey(queryResult)).foldLeft(
            Free.pure[ScanamoOpsA, Streaming[ValidatedNel[DynamoReadError, T]]](results)
          )((rs, k) => for {
            items <- rs
            more <- streamMore(Some(k))
          } yield items ++ more)
      } yield resultStream
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

    override def exec(req: ScanRequest): ScanamoOps[ScanResult] = ScanamoOps.scan(req)
  }

  object QueryResultStream extends DynamoResultStream[QueryRequest, QueryResult] {
    override def items(res: QueryResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: QueryResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(req: QueryRequest, key: util.Map[String, AttributeValue]): QueryRequest =
      req.withExclusiveStartKey(key)

    override def exec(req: QueryRequest): ScanamoOps[QueryResult] = ScanamoOps.query(req)
  }
}
