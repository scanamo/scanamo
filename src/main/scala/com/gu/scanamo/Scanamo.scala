package com.gu.scanamo

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemResult, PutItemRequest}

object Scanamo {
  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> (m: Map[String, Int], tableName: String) =>
    *     |   val putRequest = Scanamo.put(tableName)(m)
    *     |   putRequest.getTableName == tableName
    *     |   putRequest.getItem == m.mapValues(i => new AttributeValue().withN(i.toString)).asJava
    * }}}
    */
  def put[T](tableName: String)(item: T)(implicit f: DynamoFormat[T]): PutItemRequest =
    new PutItemRequest().withTableName(tableName).withItem(f.write(item).getM)

  /**
    * {{{
    * prop> import collection.convert.decorateAsJava._
    * prop> import com.amazonaws.services.dynamodbv2.model._
    * prop> (m: Map[String, Int]) =>
    *     |   Scanamo.from[Map[String, Int]](
    *     |     new GetItemResult().withItem(m.mapValues(i => new AttributeValue().withN(i.toString)).asJava)
    *     |   ) == cats.data.Validated.valid(m)
    * }}}
    */
  def from[T](result: GetItemResult)(implicit f: DynamoFormat[T]): ValidatedNel[DynamoReadError, T] =
    f.read(new AttributeValue().withM(result.getItem))
}
