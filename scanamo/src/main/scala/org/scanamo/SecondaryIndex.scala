package org.scanamo

import org.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import org.scanamo.error.DynamoReadError
import org.scanamo.ops.ScanamoOps
import org.scanamo.query.{Condition, ConditionExpression, Query, UniqueKey, UniqueKeyCondition}
import org.scanamo.request.{ScanamoQueryOptions, ScanamoQueryRequest, ScanamoScanRequest}
import scala.collection.JavaConverters._

/**
  * Represents a secondary index on a DynamoDB table.
  *
  * Can be constructed via the [[org.scanamo.Table#index index]] method on [[org.scanamo.Table Table]]
  */
sealed abstract class SecondaryIndex[V] {

  /**
    * Scan a secondary index
    *
    *
    * This will only return items with a value present in the secondary index
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String, antagonist: Option[String])
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('name -> S)('antagonist -> S) { (t, i) =>
    * ...   val table = Table[Bear](t)
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey", None))
    * ...     _ <- table.put(Bear("Yogi", "picnic baskets", Some("Ranger Smith")))
    * ...     _ <- table.put(Bear("Paddington", "marmalade sandwiches", Some("Mr Curry")))
    * ...     antagonisticBears <- table.index(i).scan()
    * ...   } yield antagonisticBears
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * List(Right(Bear(Paddington,marmalade sandwiches,Some(Mr Curry))), Right(Bear(Yogi,picnic baskets,Some(Ranger Smith))))
    * }}}
    */
  def scan(): ScanamoOps[List[Either[DynamoReadError, V]]]

  /**
    * Run a query against keys in a secondary index
    *
    * {{{
    * >>> case class GithubProject(organisation: String, repository: String, language: String, license: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)('organisation -> S, 'repository -> S)('language -> S, 'license -> S) { (t, i) =>
    * ...   val githubProjects = Table[GithubProject](t)
    * ...   val operations = for {
    * ...     _ <- githubProjects.putAll(Set(
    * ...       GithubProject("typelevel", "cats", "Scala", "MIT"),
    * ...       GithubProject("localytics", "sbt-dynamodb", "Scala", "MIT"),
    * ...       GithubProject("tpolecat", "tut", "Scala", "MIT"),
    * ...       GithubProject("guardian", "scanamo", "Scala", "Apache 2")
    * ...     ))
    * ...     scalaMIT <- githubProjects.index(i).query('language -> "Scala" and ('license -> "MIT"))
    * ...   } yield scalaMIT.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(GithubProject(typelevel,cats,Scala,MIT)), Right(GithubProject(tpolecat,tut,Scala,MIT)), Right(GithubProject(localytics,sbt-dynamodb,Scala,MIT)))
    * }}}
    */
  def query(query: Query[_]): ScanamoOps[List[Either[DynamoReadError, V]]]

  /**
    * Query or scan an index, limiting the number of items evaluated by Dynamo
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)(
    * ...   'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    * ... ) { (t, i) =>
    * ...   val transport = Table[Transport](t)
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle", "Yellow"),
    * ...       Transport("Underground", "Metropolitan", "Magenta"),
    * ...       Transport("Underground", "Central", "Red"),
    * ...       Transport("Underground", "Picadilly", "Blue"),
    * ...       Transport("Underground", "Northern", "Black")))
    * ...     somethingBeginningWithBl <- transport.index(i).limit(1).query(
    * ...       ('mode -> "Underground" and ('colour beginsWith "Bl")).descending
    * ...     )
    * ...   } yield somethingBeginningWithBl.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Picadilly,Blue)))
    * }}}
    */
  def limit(n: Int): SecondaryIndex[V]

  /**
    * Filter the results of `scan` or `query` within DynamoDB
    *
    * Note that rows filtered out still count towards your consumed capacity
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import org.scanamo.syntax._
    * >>> import org.scanamo.auto._
    *
    * >>> LocalDynamoDB.withRandomTableWithSecondaryIndex(client)(
    * ...   'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    * ... ) { (t, i) =>
    * ...   val transport = Table[Transport](t)
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle", "Yellow"),
    * ...       Transport("Underground", "Metropolitan", "Magenta"),
    * ...       Transport("Underground", "Central", "Red"),
    * ...       Transport("Underground", "Picadilly", "Blue"),
    * ...       Transport("Underground", "Northern", "Black")))
    * ...     somethingBeginningWithC <- transport.index(i)
    * ...                                   .filter('line beginsWith ("C"))
    * ...                                   .query('mode -> "Underground")
    * ...   } yield somethingBeginningWithC.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Central,Red)), Right(Transport(Underground,Circle,Yellow)))
    * }}}
    */
  def filter[C: ConditionExpression](condition: C): SecondaryIndex[V]

  def from[K: UniqueKeyCondition](key: UniqueKey[K]): SecondaryIndex[V]
}

private[scanamo] case class SecondaryIndexWithOptions[V: DynamoFormat](
  tableName: String,
  indexName: String,
  queryOptions: ScanamoQueryOptions
) extends SecondaryIndex[V] {
  def limit(n: Int): SecondaryIndexWithOptions[V] = copy(queryOptions = queryOptions.copy(limit = Some(n)))
  def from[K: UniqueKeyCondition](key: UniqueKey[K]) =
    copy(queryOptions = queryOptions.copy(exclusiveStartKey = Some(key.asAVMap.asJava)))
  def filter[C: ConditionExpression](condition: C) =
    SecondaryIndexWithOptions[V](tableName, indexName, ScanamoQueryOptions.default).filter(Condition(condition))
  def filter[T](c: Condition[T]): SecondaryIndexWithOptions[V] =
    copy(queryOptions = queryOptions.copy(filter = Some(c)))
  def scan() = ScanResultStream.stream[V](ScanamoScanRequest(tableName, Some(indexName), queryOptions)).map(_._1)
  def query(query: Query[_]) =
    QueryResultStream.stream[V](ScanamoQueryRequest(tableName, Some(indexName), query, queryOptions)).map(_._1)
}
