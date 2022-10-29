package org.scanamo

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import org.scanamo.fixtures.{ Bear, GithubProject, Key, Transport }
import org.scanamo.syntax._
import org.scanamo.query.AttributeName
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._

class SecondaryIndexTest extends AnyFunSpec with Matchers {

  val client = LocalDynamoDB.syncClient()
  val scanamo = Scanamo(client)

  it("Scan a secondary index") {
    // This will only return items with a value present in the secondary index

    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("name" -> S)("alias" -> S) { (t, i) =>
      val table = Table[Bear](t)
      val ops = for {
        _ <- table.put(Bear("Pooh", "honey", None))
        _ <- table.put(Bear("Yogi", "picnic baskets", Some("Ranger Smith")))
        _ <- table.put(Bear("Paddington", "marmalade sandwiches", Some("Mr Curry")))
        antagonisticBears <- table.index(i).scan()
      } yield antagonisticBears
      scanamo.exec(ops) should be(
        List(
          Right(Bear("Paddington", "marmalade sandwiches", Some("Mr Curry"))),
          Right(Bear("Yogi", "picnic baskets", Some("Ranger Smith")))
        )
      )
    }

  }

  it("Run a query against keys in a secondary index") {
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("organisation" -> S, "repository" -> S)(
      "language" -> S,
      "license" -> S
    ) { (t, i) =>
      val githubProjects = Table[GithubProject](t)
      val operations = for {
        _ <- githubProjects.putAll(
          Set(
            GithubProject("typelevel", "cats", "Scala", "MIT"),
            GithubProject("localytics", "sbt-dynamodb", "Scala", "MIT"),
            GithubProject("tpolecat", "tut", "Scala", "MIT"),
            GithubProject("guardian", "scanamo", "Scala", "Apache 2")
          )
        )
        scalaMIT <- githubProjects
          .index(i)
          .query(AttributeName.of("language") === "Scala" and AttributeName.of("license") === "MIT")
      } yield scalaMIT.toList
      scanamo.exec(operations) should be(
        List(
          Right(GithubProject("typelevel", "cats", "Scala", "MIT")),
          Right(GithubProject("tpolecat", "tut", "Scala", "MIT")),
          Right(GithubProject("localytics", "sbt-dynamodb", "Scala", "MIT"))
        )
      )
    }

  }

  it("Query or scan an index, limiting the number of items evaluated by Dynamo") {
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("mode" -> S, "line" -> S)("mode" -> S, "colour" -> S) {
      (t, i) =>
        val transport = Table[Transport](t)
        val operations = for {
          _ <- transport.putAll(
            Set(
              Transport("Underground", "Circle", "Yellow"),
              Transport("Underground", "Metropolitan", "Magenta"),
              Transport("Underground", "Central", "Red"),
              Transport("Underground", "Picadilly", "Blue"),
              Transport("Underground", "Northern", "Black")
            )
          )
          somethingBeginningWithBl <-
            transport
              .index(i)
              .limit(1)
              .descending
              .query(
                AttributeName.of("mode") === "Underground" and ("colour" beginsWith "Bl")
              )
        } yield somethingBeginningWithBl.toList
        scanamo.exec(operations) should be(List(Right(Transport("Underground", "Picadilly", "Blue"))))
    }
  }

  it("Filter the results of `scan` or `query` within DynamoDB") {
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("mode" -> S, "line" -> S)("mode" -> S, "colour" -> S) {
      (t, i) =>
        val transport = Table[Transport](t)
        val operations = for {
          _ <- transport.putAll(
            Set(
              Transport("Underground", "Circle", "Yellow"),
              Transport("Underground", "Metropolitan", "Magenta"),
              Transport("Underground", "Central", "Red"),
              Transport("Underground", "Picadilly", "Blue"),
              Transport("Underground", "Northern", "Black")
            )
          )
          somethingBeginningWithC <-
            transport
              .index(i)
              .filter("line" beginsWith "C")
              .query(AttributeName.of("mode") === "Underground")
        } yield somethingBeginningWithC.toList
        scanamo.exec(operations) should be(
          List(Right(Transport("Underground", "Central", "Red")), Right(Transport("Underground", "Circle", "Yellow")))
        )
    }
  }

  it("Query the index and returns the raw DynamoDB result") {
    val fmt = DynamoFormat.generic[Key]
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("mode" -> S, "line" -> S)("mode" -> S, "colour" -> S) {
      (t, i) =>
        val table = Table[Transport](t)
        val index = table.index(i)
        val operations = for {
          _ <- table.putAll(
            Set(
              Transport("Underground", "Circle", "Yellow"),
              Transport("Underground", "Metropolitan", "Magenta"),
              Transport("Underground", "Central", "Red"),
              Transport("Underground", "Picadilly", "Blue"),
              Transport("Underground", "Northern", "Black")
            )
          )
          raw <- index.limit(2).queryRaw(AttributeName.of("mode") === "Underground")
          lastKey = Option(raw.lastEvaluatedKey()).filterNot(_.isEmpty).map(DynamoObject(_))
          key = lastKey.map(_.toDynamoValue).map(fmt.read)
        } yield key
        scanamo.exec(operations) should be(Some(Right(Key("Underground", "Picadilly", "Blue"))))
    }
  }

  it("Scan the index and returns the raw DynamoDB result") {
    val fmt = DynamoFormat.generic[Key]
    LocalDynamoDB.withRandomTableWithSecondaryIndex(client)("mode" -> S, "line" -> S)("mode" -> S, "colour" -> S) {
      (t, i) =>
        val table = Table[Transport](t)
        val index = table.index(i)
        val operations = for {
          _ <- table.putAll(
            Set(
              Transport("Underground", "Circle", "Yellow"),
              Transport("Underground", "Metropolitan", "Magenta"),
              Transport("Underground", "Central", "Red"),
              Transport("Underground", "Picadilly", "Blue"),
              Transport("Underground", "Northern", "Black")
            )
          )
          raw <- index.limit(3).scanRaw
          lastKey = Option(raw.lastEvaluatedKey()).filterNot(_.isEmpty).map(DynamoObject(_))
          key = lastKey.map(_.toDynamoValue).map(fmt.read)
        } yield key
        scanamo.exec(operations) should be(Some(Right(Key("Underground", "Metropolitan", "Magenta"))))
    }
  }

}
