package com.gu.scanamo

import java.util

import cats.free.Free
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryResult, ScanResult}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{ScanamoOps, ScanamoOpsA}
import com.gu.scanamo.request.{ScanamoQueryRequest, ScanamoScanRequest}

import scala.collection.JavaConverters._

private[scanamo] trait DynamoResultStream[Req, Res] {
  type EvaluationKey = java.util.Map[String, AttributeValue]

  def limit(req: Req): Option[Int]
  def items(res: Res): java.util.List[java.util.Map[String, AttributeValue]]
  def lastEvaluatedKey(res: Res): EvaluationKey
  def withExclusiveStartKey(req: Req, key: EvaluationKey): Req
  def exec(req: Req): ScanamoOps[Res]

  def stream[T: DynamoFormat](req: Req): ScanamoOps[List[Either[DynamoReadError, T]]] = {

    def streamMore(lastKey: Option[EvaluationKey], remainingLimit: Option[Int]): ScanamoOps[List[Either[DynamoReadError, T]]] = {
      for {
        queryResult <- exec(lastKey.foldLeft(req)(withExclusiveStartKey(_, _)))
        results = items(queryResult).asScala.map(ScanamoFree.read[T]).toList
        resultsStillToGet = remainingLimit.map(_ - results.length)
        resultList <-
          Option(lastEvaluatedKey(queryResult)).filterNot(_ => resultsStillToGet.exists(_ <= 0)).foldLeft(
            Free.pure[ScanamoOpsA, List[Either[DynamoReadError, T]]](results)
          )((rs, k) => for {
            items <- rs
            more <- streamMore(Some(k), resultsStillToGet)
          } yield items ::: more)
      } yield resultList
    }
    streamMore(None, limit(req))
  }
}

private[scanamo] object DynamoResultStream {
  object ScanResultStream extends DynamoResultStream[ScanamoScanRequest, ScanResult] {
    override def items(res: ScanResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: ScanResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(req: ScanamoScanRequest, key: util.Map[String, AttributeValue]): ScanamoScanRequest =
      req.copy(options = req.options.copy(exclusiveStartKey = Some(key.asScala.toMap)))

    override def exec(req: ScanamoScanRequest): ScanamoOps[ScanResult] = ScanamoOps.scan(req)

    override def limit(req: ScanamoScanRequest): Option[Int] = req.options.limit
  }

  object QueryResultStream extends DynamoResultStream[ScanamoQueryRequest, QueryResult] {
    override def items(res: QueryResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: QueryResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(req: ScanamoQueryRequest, key: util.Map[String, AttributeValue]): ScanamoQueryRequest =
      req.copy(options = req.options.copy(exclusiveStartKey = Some(key.asScala.toMap)))

    override def exec(req: ScanamoQueryRequest): ScanamoOps[QueryResult] = ScanamoOps.query(req)

    override def limit(req: ScanamoQueryRequest): Option[Int] = req.options.limit
  }
}
