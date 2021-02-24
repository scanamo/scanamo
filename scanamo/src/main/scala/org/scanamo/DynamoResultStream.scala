/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo

import cats.{ Monad, MonoidK }
import cats.MonoidK.ops._
import cats.free.{ Free, FreeT }
import cats.syntax.semigroupk._
import software.amazon.awssdk.services.dynamodb.model.{ QueryResponse, ScanResponse }
import org.scanamo.ops.{ ScanamoOps, ScanamoOpsA, ScanamoOpsT }
import org.scanamo.request.{ ScanamoQueryRequest, ScanamoScanRequest }

private[scanamo] trait DynamoResultStream[Req, Res] {
  def limit(req: Req): Option[Int]
  def startKey(req: Req): Option[DynamoObject]
  def items(res: Res): List[DynamoObject]
  def lastEvaluatedKey(res: Res): Option[DynamoObject]
  def scannedCount(res: Res): Option[Int]
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
        newLimit = limit(req).map(_ - scannedCount(res).getOrElse(0))
        lastKey = lastEvaluatedKey(res).filterNot(_.isEmpty)
        result <-
          lastKey
            .filterNot(_ => newLimit.exists(_ <= 0))
            .foldLeft(
              Free
                .pure[ScanamoOpsA, (List[Either[DynamoReadError, T]], Option[DynamoObject])]((results, lastKey))
            )((rs, k) =>
              for {
                results <- rs
                newReq = prepare(newLimit, k)(req)
                more <- streamMore(newReq)
              } yield (results._1 ::: more._1, more._2)
            )
      } yield result
    streamMore(req)
  }

  def streamTo[M[_]: Monad, T: DynamoFormat](
    req: Req,
    pageSize: Int
  )(implicit M: MonoidK[M]): ScanamoOpsT[M, List[Either[DynamoReadError, T]]] = {
    def streamMore(req: Req, l: Int): ScanamoOpsT[M, List[Either[DynamoReadError, T]]] =
      exec(withLimit(l)(req)).toFreeT[M].flatMap { res =>
        val results = items(res).map(ScanamoFree.read[T])
        if (results.isEmpty)
          FreeT.liftT(M.empty)
        else {
          val l1 = limit(req).fold(pageSize)(l => Math.min(pageSize, l - results.length))
          lastEvaluatedKey(res)
            .filterNot(_ => l1 <= 0)
            .foldLeft(FreeT.pure[ScanamoOpsA, M, List[Either[DynamoReadError, T]]](results))((res, k) =>
              res <+> streamMore(withExclusiveStartKey(k)(req), l1)
            )
        }
      }
    streamMore(req, limit(req).fold(pageSize)(Math.min(_, pageSize)))
  }
}

private[scanamo] object DynamoResultStream {
  object ScanResponseStream extends DynamoResultStream[ScanamoScanRequest, ScanResponse] {
    final def items(res: ScanResponse) =
      res.items.stream.reduce[List[DynamoObject]](Nil, (m, xs) => DynamoObject(xs) :: m, _ ++ _).reverse
    final def lastEvaluatedKey(res: ScanResponse) = Option(res.lastEvaluatedKey).map(DynamoObject(_))
    final def scannedCount(res: ScanResponse) = Option(res.scannedCount().intValue())
    final def withExclusiveStartKey(key: DynamoObject) =
      req => req.copy(options = req.options.copy(exclusiveStartKey = Some(key)))
    final def withLimit(limit: Int) =
      req => req.copy(options = req.options.copy(limit = Some(limit)))

    final def exec(req: ScanamoScanRequest) = ScanamoOps.scan(req)

    final def limit(req: ScanamoScanRequest) = req.options.limit
    final def startKey(req: ScanamoScanRequest) = req.options.exclusiveStartKey
  }

  object QueryResponseStream extends DynamoResultStream[ScanamoQueryRequest, QueryResponse] {
    final def items(res: QueryResponse) =
      res.items.stream.reduce[List[DynamoObject]](Nil, (m, xs) => DynamoObject(xs) :: m, _ ++ _).reverse
    final def lastEvaluatedKey(res: QueryResponse) = Option(res.lastEvaluatedKey).map(DynamoObject(_))
    final def scannedCount(res: QueryResponse) = Option(res.scannedCount().intValue())
    final def withExclusiveStartKey(key: DynamoObject) =
      req => req.copy(options = req.options.copy(exclusiveStartKey = Some(key)))
    final def withLimit(limit: Int) =
      req => req.copy(options = req.options.copy(limit = Some(limit)))

    final def exec(req: ScanamoQueryRequest) = ScanamoOps.query(req)

    final def limit(req: ScanamoQueryRequest) = req.options.limit
    final def startKey(req: ScanamoQueryRequest) = req.options.exclusiveStartKey
  }
}
