# Scanamo

[![scanamo Scala version support](https://index.scala-lang.org/scanamo/scanamo/scanamo/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/scanamo/scanamo/scanamo)
[![CI](https://github.com/scanamo/scanamo/actions/workflows/ci.yml/badge.svg)](https://github.com/scanamo/scanamo/actions/workflows/ci.yml)

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala 
simpler and less error-prone. The library is currently maintained by Roberto Tyley (@rtyley).

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Installation
------------

```scala
libraryDependencies += "org.scanamo" %% "scanamo" % "1.0.0"
```

Basic Usage
-----------

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

_Note: the `LocalDynamoDB` object is provided by the `scanamo-testkit` package._

For more details, please see the [Scanamo site](http://www.scanamo.org).

License
-------

Scanamo is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) (the "License"); 
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.
