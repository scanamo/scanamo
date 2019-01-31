package org.scanamo

import java.util

import cats.free.Free
import org.scanamo.error.DynamoReadError
import org.scanamo.ops.{ScanamoOps, ScanamoOpsA}
import org.scanamo.request.{ScanamoQueryRequest, ScanamoScanRequest}
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, QueryResponse, ScanResponse}

import scala.collection.JavaConverters._

private[scanamo] trait DynamoResultStream[Req, Res] {

  def limit(req: Req): Option[Int]
  def startKey(req: Req): Option[EvaluationKey]
  def items(res: Res): java.util.List[java.util.Map[String, AttributeValue]]
  def lastEvaluatedKey(res: Res): EvaluationKey
  def withExclusiveStartKey(key: EvaluationKey): Req => Req
  def withLimit(limit: Int): Req => Req
  def exec(req: Req): ScanamoOps[Res]

  def prepare(limit: Option[Int], lastEvaluatedKey: EvaluationKey): Req => Req =
    withExclusiveStartKey(lastEvaluatedKey).andThen(limit.map(withLimit).getOrElse(identity[Req](_)))

  def stream[T: DynamoFormat](req: Req): ScanamoOps[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] = {

    def streamMore(req: Req): ScanamoOps[(List[Either[DynamoReadError, T]], Option[EvaluationKey])] =
      for {
        res <- exec(req)
        results = items(res).asScala.map(ScanamoFree.read[T]).toList
        newLimit = limit(req).map(_ - results.length)
        lastKey = Option(lastEvaluatedKey(res)).filterNot(_.isEmpty)
        result <- lastKey
          .filterNot(_ => newLimit.exists(_ <= 0))
          .foldLeft(
            Free
              .pure[ScanamoOpsA, (List[Either[DynamoReadError, T]], Option[EvaluationKey])]((results, lastKey))
          )(
            (rs, k) =>
              for {
                results <- rs
                newReq = prepare(newLimit, k)(req)
                more <- streamMore(newReq)
              } yield (results._1 ::: more._1, more._2)
          )
      } yield result
    streamMore(req)
  }
}

private[scanamo] object DynamoResultStream {
  object ScanResultStream extends DynamoResultStream[ScanamoScanRequest, ScanResponse] {
    override def items(res: ScanResponse): util.List[util.Map[String, AttributeValue]] = res.items()
    override def lastEvaluatedKey(res: ScanResponse): EvaluationKey = res.lastEvaluatedKey()
    override def withExclusiveStartKey(key: EvaluationKey) =
      req => req.copy(options = req.options.copy(exclusiveStartKey = Some(key)))
    override def withLimit(limit: Int) =
      req => req.copy(options = req.options.copy(limit = Some(limit)))

    override def exec(req: ScanamoScanRequest): ScanamoOps[ScanResponse] = ScanamoOps.scan(req)

    override def limit(req: ScanamoScanRequest): Option[Int] = req.options.limit
    override def startKey(req: ScanamoScanRequest) = req.options.exclusiveStartKey
  }

  object QueryResultStream extends DynamoResultStream[ScanamoQueryRequest, QueryResponse] {
    override def items(res: QueryResponse): util.List[util.Map[String, AttributeValue]] = res.items()
    override def lastEvaluatedKey(res: QueryResponse): EvaluationKey = res.lastEvaluatedKey()
    override def withExclusiveStartKey(key: EvaluationKey) =
      req => req.copy(options = req.options.copy(exclusiveStartKey = Some(key)))
    override def withLimit(limit: Int) =
      req => req.copy(options = req.options.copy(limit = Some(limit)))

    override def exec(req: ScanamoQueryRequest): ScanamoOps[QueryResponse] = ScanamoOps.query(req)

    override def limit(req: ScanamoQueryRequest): Option[Int] = req.options.limit
    override def startKey(req: ScanamoQueryRequest) = req.options.exclusiveStartKey
  }
}
