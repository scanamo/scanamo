---
layout: home
section: home
position: 1
---

| CI | Coverage | Release | Issues | Users | Chat |
| --- | --- | --- | --- | --- | --- |
| [![Build Status][Badge-Travis]][Link-Travis] | [![Coverage Status][Badge-Codecov]][Link-Codecov] | [![Release Artifacts][Badge-MavenReleases]][Link-MavenReleases] | [![Average time to resolve an issue][Badge-IsItMaintained]][Link-IsItMaintained] | [![Scaladex dependencies badge][Badge-Scaladex]][Link-Scaladex] | [![Gitter][Badge-Gitter]][Link-Gitter] |

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala
simpler and less error-prone.

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Quick start
-----------

Note: the `LocalDynamoDB` object is provided by the `scanamo-testkit` package.

Scanamo is published for Scala 2.11 and 2.12 to Maven Central, so just add the following to your `build.sbt`:

```scala
libraryDependencies += "com.gu" %% "scanamo" % "1.0.0-M8"
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


[Link-Codecov]: https://coveralls.io/github/guardian/scanamo?branch=master "Codecov"
[Link-IsItMaintained]: https://isitmaintained.com/project/scanamo/scanamo "Average time to resolve an issue"
[Link-Scaladex]: https://index.scala-lang.org/search?q=dependencies:scanamo/scanamo "Scaladex"
[Link-MavenReleases]: https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.12 "Maven Releases"
[Link-Travis]: https://travis-ci.org/scanamo/scanamo "Travis CI"
[Link-Gitter]: https://gitter.im/guardian/scanamo "Gitter chat"

[Badge-Codecov]: https://coveralls.io/repos/github/guardian/scanamo/badge.svg?branch=master "Codecov"
[Badge-IsItMaintained]: http://isitmaintained.com/badge/resolution/scanamo/scanamo.svg "Average time to resolve an issue"
[Badge-Scaladex]: https://index.scala-lang.org/count.svg?q=dependencies:scanamo/scanamo&subject=scaladex "Scaladex"
[Badge-MavenReleases]: https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11/badge.svg "Maven Releases"
[Badge-Travis]: https://travis-ci.org/scanamo/scanamo.svg?branch=master "Travis CI"
[Badge-Gitter]: https://badges.gitter.im/guardian/scanamo.svg "Gitter chat"