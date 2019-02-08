package org.scanamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import org.scanamo.ops.{ScanamoOps, ZioInterpreter}
import scalaz.zio.IO
import scalaz.zio.interop.catz._

object ScanamoZio {

  def exec[A](client: AmazonDynamoDBAsync)(op: ScanamoOps[A]): IO[AmazonDynamoDBException, A] =
    op.foldMap(ZioInterpreter.effect(client))

}
