---
layout: home
section: home
position: 1
---

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.12) [![Build Status](https://travis-ci.org/guardian/scanamo.svg?branch=master)](https://travis-ci.org/guardian/scanamo) [![Coverage Status](https://coveralls.io/repos/github/guardian/scanamo/badge.svg?branch=master)](https://coveralls.io/github/guardian/scanamo?branch=master) [![Chat on gitter](https://badges.gitter.im/guardian/scanamo.svg)](https://gitter.im/guardian/scanamo)

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala
simpler and less error-prone.

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Quick start
-----------

Scanamo is published for Scala 2.11 and 2.12 to Maven Central, so just add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "com.gu" %% "scanamo" % "0.9.1"
)
```

then, given a table and some case classes

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._
 
val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
val farmersTableResult = LocalDynamoDB.createTable(client)("farmer")('name -> S)

case class Farm(animals: List[String])
case class Farmer(name: String, age: Long, farm: Farm)
```
we can simply `put` and `get` items from Dynamo, without boilerplate or reflection

```tut:book
val table = Table[Farmer]("farmer")

Scanamo.exec(client)(table.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow")))))
Scanamo.exec(client)(table.get('name -> "McDonald"))
```

Scanamo supports most other DynamoDB [operations](operations.html), beyond
the basic `Put` and `Get`.

The translation between Dynamo items and Scala types is handled by a type class
called [DynamoFormat](dynamo-format.html).

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).