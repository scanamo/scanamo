package com.gu.scanamo

import java.util

import cats.data._
import cats.free.Free
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryRequest, QueryResult, ScanRequest, ScanResult}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.{ScanamoOps, ScanamoOpsA}

import collection.convert.decorateAsScala._

private[scanamo] trait DynamoResultStream[Req, Res] {
  type EvaluationKey = java.util.Map[String, AttributeValue]

  def limit(req: Req): Option[Int]
  def items(res: Res): java.util.List[java.util.Map[String, AttributeValue]]
  def lastEvaluatedKey(res: Res): EvaluationKey
  def withExclusiveStartKey(req: Req, key: EvaluationKey): Req
  def exec(req: Req): ScanamoOps[Res]

  def stream[T: DynamoFormat](req: Req): ScanamoOps[Stream[Xor[DynamoReadError, T]]] = {

    def streamMore(lastKey: Option[EvaluationKey], remainingLimit: Option[Int]): ScanamoOps[Stream[Xor[DynamoReadError, T]]] = {
      for {
        queryResult <- exec(lastKey.foldLeft(req)(withExclusiveStartKey(_, _)))
        results = items(queryResult).asScala.map(ScanamoFree.read[T]).toStream
        resultsStillToGet = remainingLimit.map(_ - results.length)
        resultStream <-
          Option(lastEvaluatedKey(queryResult)).filterNot(_ => resultsStillToGet.exists(_ <= 0)).foldLeft(
            Free.pure[ScanamoOpsA, Stream[Xor[DynamoReadError, T]]](results)
          )((rs, k) => for {
            items <- rs
            more <- streamMore(Some(k), resultsStillToGet)
          } yield items #::: more)
      } yield resultStream
    }
    streamMore(None, limit(req))
  }
}

private[scanamo] object DynamoResultStream {
  object ScanResultStream extends DynamoResultStream[ScanRequest, ScanResult] {
    override def items(res: ScanResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: ScanResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(req: ScanRequest, key: util.Map[String, AttributeValue]): ScanRequest =
      req.withExclusiveStartKey(key)

    override def exec(req: ScanRequest): ScanamoOps[ScanResult] = ScanamoOps.scan(req)

    override def limit(req: ScanRequest): Option[Int] = Option(req.getLimit).map(_.intValue)
  }

  object QueryResultStream extends DynamoResultStream[QueryRequest, QueryResult] {
    override def items(res: QueryResult): util.List[util.Map[String, AttributeValue]] = res.getItems
    override def lastEvaluatedKey(res: QueryResult): util.Map[String, AttributeValue] = res.getLastEvaluatedKey
    override def withExclusiveStartKey(req: QueryRequest, key: util.Map[String, AttributeValue]): QueryRequest =
      req.withExclusiveStartKey(key)

    override def exec(req: QueryRequest): ScanamoOps[QueryResult] = ScanamoOps.query(req)

    override def limit(req: QueryRequest): Option[Int] = Option(req.getLimit).map(_.intValue)
  }
}
