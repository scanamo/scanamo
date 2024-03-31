---
title: Overview
sidebar_position: 1
---

[![scanamo Scala version support](https://index.scala-lang.org/scanamo/scanamo/scanamo/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/scanamo/scanamo/scanamo)
[![CI](https://github.com/scanamo/scanamo/actions/workflows/ci.yml/badge.svg)](https://github.com/scanamo/scanamo/actions/workflows/ci.yml)

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala
simpler and less error-prone.

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Quick start
-----------

Note: the `LocalDynamoDB` object is provided by the `scanamo-testkit` package.

Scanamo is published to Maven Central, so just add the following to your `build.sbt`:

```sbt
libraryDependencies += "org.scanamo" %% "scanamo" % "@VERSION@"
```

then, given a table and some case classes

```scala mdoc:silent
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._

val client = LocalDynamoDB.syncClient()
val scanamo = Scanamo(client)
val farmersTableResult = LocalDynamoDB.createTable(client)("farmer")("name" -> S)

case class Farm(animals: List[String])
case class Farmer(name: String, age: Long, farm: Farm)
```
we can simply `put` and `get` items from Dynamo, without boilerplate or reflection

```scala mdoc
val table = Table[Farmer]("farmer")

scanamo.exec(table.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow")))))
scanamo.exec(table.get("name" === "McDonald"))
```

Scanamo supports most other DynamoDB [operations](operations.md), beyond
the basic `Put` and `Get`.

The translation between Dynamo items and Scala types is handled by a type class
called [DynamoFormat](dynamo-format.md).

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

```scala mdoc:invisible
LocalDynamoDB.deleteTable(client)("farmer")
```
