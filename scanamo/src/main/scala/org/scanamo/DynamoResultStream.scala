package org.scanamo

import cats.free.Free
import com.amazonaws.services.dynamodbv2.model.{QueryResult, ScanResult}
import org.scanamo.error.DynamoReadError
import org.scanamo.ops.{ScanamoOps, ScanamoOpsA}
import org.scanamo.request.{ScanamoQueryRequest, ScanamoScanRequest}

private[scanamo] trait DynamoResultStream[Req, Res] {

  def limit(req: Req): Option[Int]
  def startKey(req: Req): Option[DynamoObject]
  def items(res: Res): List[DynamoObject]
  def lastEvaluatedKey(res: Res): Option[DynamoObject]
  def withExclusiveStartKey(key: DynamoObject): Req => Req
  def withLimit(limit: Int): Req => Req
  def exec(req: Req): ScanamoOps[Res]

  def prepare(limit: Option[Int], lastEvaluatedKey: DynamoObject): Req => Req =
    withExclusiveStartKey(lastEvaluatedKey).andThen(limit.map(withLimit).getOrElse(identity[Req](_)))

  def stream[T: DynamoFormat](req: Req): ScanamoOps[(List[Either[DynamoReadError, T]], Option[DynamoObject])] = {

    def streamMore(req: Req): ScanamoOps[(List[Either[DynamoReadError, T]], Option[DynamoObject])] =
      for {
        res <- exec(req)
        results = items(res).map(ScanamoFree.read[T])
        newLimit = limit(req).map(_ - results.length)
        lastKey = lastEvaluatedKey(res).filterNot(_.isEmpty)
        result <- lastKey
          .filterNot(_ => newLimit.exists(_ <= 0))
          .foldLeft(
            Free
              .pure[ScanamoOpsA, (List[Either[DynamoReadError, T]], Option[DynamoObject])]((results, lastKey))
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
  object ScanResultStream extends DynamoResultStream[ScanamoScanRequest, ScanResult] {
    final def items(res: ScanResult) =
      res.getItems.stream.reduce[List[DynamoObject]](Nil, (m, xs) => DynamoObject(xs) :: m, _ ++ _)
    final def lastEvaluatedKey(res: ScanResult) = Option(res.getLastEvaluatedKey).map(DynamoObject(_))
    final def withExclusiveStartKey(key: DynamoObject) =
      req => req.copy(options = req.options.copy(exclusiveStartKey = Some(key)))
    final def withLimit(limit: Int) =
      req => req.copy(options = req.options.copy(limit = Some(limit)))

    final def exec(req: ScanamoScanRequest) = ScanamoOps.scan(req)

    final def limit(req: ScanamoScanRequest) = req.options.limit
    final def startKey(req: ScanamoScanRequest) = req.options.exclusiveStartKey
  }

  object QueryResultStream extends DynamoResultStream[ScanamoQueryRequest, QueryResult] {
    final def items(res: QueryResult) =
      res.getItems.stream.reduce[List[DynamoObject]](Nil, (m, xs) => DynamoObject(xs) :: m, _ ++ _)
    final def lastEvaluatedKey(res: QueryResult) = Option(res.getLastEvaluatedKey).map(DynamoObject(_))
    final def withExclusiveStartKey(key: DynamoObject) =
      req => req.copy(options = req.options.copy(exclusiveStartKey = Some(key)))
    final def withLimit(limit: Int) =
      req => req.copy(options = req.options.copy(limit = Some(limit)))

    final def exec(req: ScanamoQueryRequest) = ScanamoOps.query(req)

    final def limit(req: ScanamoQueryRequest) = req.options.limit
    final def startKey(req: ScanamoQueryRequest) = req.options.exclusiveStartKey
  }
}
