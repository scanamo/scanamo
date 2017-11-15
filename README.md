Scanamo [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.12) [![Build Status](https://travis-ci.org/guardian/scanamo.svg?branch=master)](https://travis-ci.org/guardian/scanamo) [![Coverage Status](https://coveralls.io/repos/github/guardian/scanamo/badge.svg?branch=master)](https://coveralls.io/github/guardian/scanamo?branch=master) [![Chat on gitter](https://badges.gitter.im/guardian/scanamo.svg)](https://gitter.im/guardian/scanamo)
=======

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala 
simpler and less error-prone.

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Installation
------------

```scala
libraryDependencies ++= Seq(
  "com.gu" %% "scanamo" % "1.0.0-M3"
)
```

Scanamo is published for Scala 2.12 and Scala 2.11

Basic Usage
-----------

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._
 
scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val farmersTableResult = LocalDynamoDB.createTable(client)("farmer")('name -> S)

scala> case class Farm(animals: List[String])
scala> case class Farmer(name: String, age: Long, farm: Farm)
scala> val table = Table[Farmer]("farmer")

scala> val ops = for {
     |   _ <- table.putAll(Set(
     |       Farmer("McDonald", 156L, Farm(List("sheep", "cow"))),
     |       Farmer("Boggis", 43L, Farm(List("chicken")))
     |     ))
     |   mcdonald <- table.get('name -> "McDonald")
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
