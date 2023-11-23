| CI | Coverage | Release | Issues | Chat |
| --- | --- | --- | --- | --- |
| [![.github/workflows/ci.yaml](https://github.com/scanamo/scanamo/actions/workflows/ci.yaml/badge.svg)](https://github.com/scanamo/scanamo/actions/workflows/ci.yaml) | [![Coverage Status][Badge-Codecov]][Link-Codecov] | [![Release Artifacts][Badge-MavenReleases]][Link-MavenReleases] | [![Average time to resolve an issue][Badge-IsItMaintained]][Link-IsItMaintained] | [![Gitter][Badge-Gitter]][Link-Gitter] |

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala 
simpler and less error-prone.

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Installation
------------

```scala
libraryDependencies += "org.scanamo" %% "scanamo" % "1.0.0-M23"
```

Scanamo is published for Scala 2.13 and Scala 2.12.

Basic Usage
-----------

Note: the `LocalDynamoDB` object is provided by the `scanamo-testkit` package.

```scala
scala> import org.scanamo._
scala> import org.scanamo.syntax._
scala> import org.scanamo.generic.auto._
 
scala> val client = LocalDynamoDB.client()
scala> import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
scala> val farmersTableResult = LocalDynamoDB.createTable(client)("farmer")("name" -> S)

scala> case class Farm(animals: List[String])
scala> case class Farmer(name: String, age: Long, farm: Farm)
scala> val table = Table[Farmer]("farmer")

scala> val ops = for {
     |   _ <- table.putAll(Set(
     |       Farmer("McDonald", 156L, Farm(List("sheep", "cow"))),
     |       Farmer("Boggis", 43L, Farm(List("chicken")))
     |     ))
     |   mcdonald <- table.get("name" -> "McDonald")
     | } yield mcdonald
scala> Scanamo.exec(client)(ops)
res1: Option[Either[error.DynamoReadError, Farmer]] = Some(Right(Farmer(McDonald,156,Farm(List(sheep, cow)))))
```

For more details, please see the [Scanamo site](http://www.scanamo.org).

License
-------

Scanamo is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) (the "License"); 
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.


[Link-Codecov]: https://coveralls.io/github/scanamo/scanamo?branch=master "Codecov"
[Link-IsItMaintained]: https://isitmaintained.com/project/scanamo/scanamo "Average time to resolve an issue"
[Link-Scaladex]: https://index.scala-lang.org/search?q=dependencies:scanamo/scanamo "Scaladex"
[Link-MavenReleases]: https://index.scala-lang.org/scanamo/scanamo/scanamo "Maven Releases"
[Link-Gitter]: https://gitter.im/scanamo/scanamo "Gitter chat"

[Badge-Codecov]: https://coveralls.io/repos/github/scanamo/scanamo/badge.svg?branch=master "Codecov"
[Badge-IsItMaintained]: http://isitmaintained.com/badge/resolution/scanamo/scanamo.svg "Average time to resolve an issue"
[Badge-Scaladex]: https://index.scala-lang.org/count.svg?q=dependencies:scanamo/scanamo&subject=scaladex "Scaladex"
[Badge-MavenReleases]: https://index.scala-lang.org/scanamo/scanamo/scanamo/latest-by-scala-version.svg "Maven Releases"
[Badge-Gitter]: https://badges.gitter.im/scanamo/scanamo.svg "Gitter chat"
