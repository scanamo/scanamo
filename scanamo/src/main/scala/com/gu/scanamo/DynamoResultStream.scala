package com.gu.scanamo

import java.util

import cats.free.Free
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryResult, ScanResult}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{ScanamoOps, ScanamoOpsA}
import com.gu.scanamo.request.{ScanamoQueryRequest, ScanamoScanRequest}

import scala.collection.JavaConverters._

private[scanamo] trait DynamoResultStream[Req, Res] {

  def limit(req: Req): Option[Int]
  def startKey(req: Req): Option[Map[String, AttributeValue]]
  def items(res: Res): java.util.List[java.util.Map[String, AttributeValue]]
  def lastEvaluatedKey(res: Res): EvaluationKey
  def withExclusiveStartKey(key: EvaluationKey, req: Req): Req
  def withLimit(limit: Int, req: Req): Req
  def exec(req: Req): ScanamoOps[Res]

  def stream[T: DynamoFormat](req: Req): ScanamoOps[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] = {

    def streamMore(req: Req): ScanamoOps[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] = {
      for {
        res <- exec(req)
        results = items(res).asScala.map(ScanamoFree.read[T]).toList
        newLimit = limit(req).map(_ - results.length)
        lastKey = Option(lastEvaluatedKey(res)).filterNot(_.isEmpty)
        result <-
          lastKey.filterNot(_ => newLimit.exists(_ <= 0)).foldLeft(
            Free.pure[ScanamoOpsA, (List[Either[DynamoReadError, T]], Option[EvaluationKey])]((results, None))
          )((rs, k) => 
            for {
              results <- rs
              newReq = newLimit.foldRight(withExclusiveStartKey(k, req))(withLimit(_, _))
              more <- streamMore(newReq)
            } yield (results._1 ::: more._1, more._2)
          )
      } yield result
    }
    streamMore(req)
  }
}

private[scanamo] object DynamoResultStream {
  object ScanResultStream extends DynamoResultStream[ScanamoScanRequest, ScanResult] {
    override def items(res: ScanResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: ScanResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(key: util.Map[String, AttributeValue], req: ScanamoScanRequest): ScanamoScanRequest =
      req.copy(options = req.options.copy(exclusiveStartKey = Some(key.asScala.toMap)))
    override def withLimit(limit: Int, req: ScanamoScanRequest): ScanamoScanRequest =
      req.copy(options = req.options.copy(limit = Some(limit)))

    override def exec(req: ScanamoScanRequest): ScanamoOps[ScanResult] = ScanamoOps.scan(req)

    override def limit(req: ScanamoScanRequest): Option[Int] = req.options.limit
    override def startKey(req: ScanamoScanRequest) = req.options.exclusiveStartKey
  }

  object QueryResultStream extends DynamoResultStream[ScanamoQueryRequest, QueryResult] {
    override def items(res: QueryResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: QueryResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(key: util.Map[String, AttributeValue], req: ScanamoQueryRequest): ScanamoQueryRequest =
      req.copy(options = req.options.copy(exclusiveStartKey = Some(key.asScala.toMap)))
    override def withLimit(limit: Int, req: ScanamoQueryRequest): ScanamoQueryRequest =
      req.copy(options = req.options.copy(limit = Some(limit)))

    override def exec(req: ScanamoQueryRequest): ScanamoOps[QueryResult] = ScanamoOps.query(req)

    override def limit(req: ScanamoQueryRequest): Option[Int] = req.options.limit
    override def startKey(req: ScanamoQueryRequest) = req.options.exclusiveStartKey
  }
}
