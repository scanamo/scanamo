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
  def startKey(req: Req): Option[Map[String, AttributeValue]]
  def items(res: Res): java.util.List[java.util.Map[String, AttributeValue]]
  def lastEvaluatedKey(res: Res): EvaluationKey
  def withExclusiveStartKey(key: EvaluationKey): Req => Req
  def withLimit(limit: Int): Req => Req
  def exec(req: Req): ScanamoOps[Res]

  def stream[T: DynamoFormat](req: Req): ScanamoOps[List[Either[DynamoReadError, T]]] = {

    def streamMore(req: Req): ScanamoOps[List[Either[DynamoReadError, T]]] = {
      for {
        res <- exec(req)
        results = items(res).asScala.map(ScanamoFree.read[T]).toList
        resultsStillToGet = limit(req).map(_ - results.length).getOrElse(Int.MaxValue)
        resultList <-
          Option(lastEvaluatedKey(res)).filterNot(_ => resultsStillToGet <= 0).foldLeft(
            Free.pure[ScanamoOpsA, List[Either[DynamoReadError, T]]](results)
          )((rs, k) => for {
            items <- rs
            more <- streamMore((withExclusiveStartKey(k) andThen withLimit(resultsStillToGet))(req))
          } yield items ::: more)
      } yield resultList
    }
    streamMore(req)
  }
}

private[scanamo] object DynamoResultStream {
  object ScanResultStream extends DynamoResultStream[ScanamoScanRequest, ScanResult] {
    override def items(res: ScanResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: ScanResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(key: util.Map[String, AttributeValue]) =
      req => req.copy(options = req.options.copy(exclusiveStartKey = Some(key.asScala.toMap)))
    override def withLimit(limit: Int) =
      req => req.copy(options = req.options.copy(limit = Some(limit)))

    override def exec(req: ScanamoScanRequest): ScanamoOps[ScanResult] = ScanamoOps.scan(req)

    override def limit(req: ScanamoScanRequest): Option[Int] = req.options.limit
    override def startKey(req: ScanamoScanRequest) = req.options.exclusiveStartKey
  }

  object QueryResultStream extends DynamoResultStream[ScanamoQueryRequest, QueryResult] {
    override def items(res: QueryResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: QueryResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(key: util.Map[String, AttributeValue]) =
      req => req.copy(options = req.options.copy(exclusiveStartKey = Some(key.asScala.toMap)))
    override def withLimit(limit: Int) =
      req => req.copy(options = req.options.copy(limit = Some(limit)))

    override def exec(req: ScanamoQueryRequest): ScanamoOps[QueryResult] = ScanamoOps.query(req)

    override def limit(req: ScanamoQueryRequest): Option[Int] = req.options.limit
    override def startKey(req: ScanamoQueryRequest) = req.options.exclusiveStartKey
  }
}
