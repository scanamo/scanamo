package com.gu.scanamo

import com.gu.scanamo.DynamoResultStream.{QueryResultStream, ScanResultStream}
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query.{Condition, ConditionExpression, Query}
import com.gu.scanamo.request.{ScanamoQueryOptions, ScanamoQueryRequest, ScanamoScanRequest}

sealed abstract class Index[V] {

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
    * >>> val table = Table[Bear]("bears")
    *
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("bears", "antagonist")('name -> S)('antagonist -> S) {
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey", None))
    * ...     _ <- table.put(Bear("Yogi", "picnic baskets", Some("Ranger Smith")))
    * ...     _ <- table.put(Bear("Paddington", "marmalade sandwiches", Some("Mr Curry")))
    * ...     antagonisticBears <- table.index("antagonist").scan()
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
    * >>> val githubProjects = Table[GithubProject]("github-projects")
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("github-projects", "language-license")('organisation -> S, 'repository -> S)('language -> S, 'license -> S) {
    * ...   val operations = for {
    * ...     _ <- githubProjects.putAll(Set(
    * ...       GithubProject("typelevel", "cats", "Scala", "MIT"),
    * ...       GithubProject("localytics", "sbt-dynamodb", "Scala", "MIT"),
    * ...       GithubProject("tpolecat", "tut", "Scala", "MIT"),
    * ...       GithubProject("guardian", "scanamo", "Scala", "Apache 2")
    * ...     ))
    * ...     scalaMIT <- githubProjects.index("language-license").query('language -> "Scala" and ('license -> "MIT"))
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
    * >>> val transport = Table[Transport]("transport")
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")(
    * ...   'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    * ... ) {
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle", "Yellow"),
    * ...       Transport("Underground", "Metropolitan", "Magenta"),
    * ...       Transport("Underground", "Central", "Red"),
    * ...       Transport("Underground", "Picadilly", "Blue"),
    * ...       Transport("Underground", "Northern", "Black")))
    * ...     somethingBeginningWithBl <- transport.index("colour-index").limit(1).query(
    * ...       ('mode -> "Underground" and ('colour beginsWith "Bl")).descending
    * ...     )
    * ...   } yield somethingBeginningWithBl.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Picadilly,Blue)))
    * }}}
    */
  def limit(n: Int): Index[V]
  def filter[C: ConditionExpression](condition: C): Index[V]
}

private[scanamo] case class IndexWithOptions[V: DynamoFormat](tableName: String, indexName: String, queryOptions: ScanamoQueryOptions) extends Index[V] {
  def limit(n: Int): IndexWithOptions[V] = copy(queryOptions = queryOptions.copy(limit = Some(n)))
  def filter[C: ConditionExpression](condition: C) =
    IndexWithOptions[V](tableName, indexName, ScanamoQueryOptions.default).filter(Condition(condition))
  def filter[T](c: Condition[T]): IndexWithOptions[V] = copy(queryOptions = queryOptions.copy(filter = Some(c)))
  def scan() = ScanResultStream.stream[V](ScanamoScanRequest(tableName, Some(indexName), queryOptions))
  def query(query: Query[_]) = QueryResultStream.stream[V](ScanamoQueryRequest(tableName, Some(indexName), query, queryOptions))
}