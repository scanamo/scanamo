package org.scanamo

import org.scanamo.ops.{ScanamoInterpreters, ScanamoOps}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

/**
  * Provides a simplified interface for reading and writing case classes to DynamoDB
  *
  * To avoid blocking, use [[org.scanamo.ScanamoAsync]]
  */
object Scanamo {

  def exec[A](client: DynamoDbClient)(op: ScanamoOps[A]): A = op.foldMap(ScanamoInterpreters.id(client))
}
