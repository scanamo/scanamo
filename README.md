Scanamo [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11) [![Build Status](https://travis-ci.org/guardian/scanamo.svg?branch=master)](https://travis-ci.org/guardian/scanamo) [![Coverage Status](https://coveralls.io/repos/github/guardian/scanamo/badge.svg?branch=master)](https://coveralls.io/github/guardian/scanamo?branch=master)
=======

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala 
simpler and less error-prone.

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Installation
------------

```scala
libraryDependencies ++= Seq(
  "com.gu" %% "scanamo" % "0.2.0"
)
```

Usage
-----

You can simply `put` case classes to and `get` them back from DynamoDB:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._
 
scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val farmersTableResult = LocalDynamoDB.createTable(client)("farmer")('name -> S)

scala> case class Farm(animals: List[String])
scala> case class Farmer(name: String, age: Long, farm: Farm)

scala> val putResult = Scanamo.put(client)("farmer")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
scala> Scanamo.get[Farmer](client)("farmer")('name -> "McDonald")
res1: Option[cats.data.ValidatedNel[DynamoReadError, Farmer]] = Some(Valid(Farmer(McDonald,156,Farm(List(sheep, cow)))))
```

It's also possible to make more complex queries:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._
 
scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val transportTableResult = LocalDynamoDB.createTable(client)("transports")('mode -> S, 'line -> S)
scala> case class Transport(mode: String, line: String)

scala> val lines = Scanamo.putAll(client)("transports")(List(
     |       Transport("Underground", "Circle"),
     |       Transport("Underground", "Metropolitan"),
     |       Transport("Underground", "Central")
     | ))
     
scala> Scanamo.query[Transport](client)("transports")('mode -> "Underground" and ('line beginsWith "C")).toList
res1: List[cats.data.ValidatedNel[DynamoReadError, Transport]] = List(Valid(Transport(Underground,Central)), Valid(Transport(Underground,Circle)))
```

Scanamo also supports asynchronous calls to Dynamo:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._

scala> import scala.concurrent.duration._
scala> import scala.concurrent.ExecutionContext.Implicits.global
 
scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val farmersTableResult = LocalDynamoDB.createTable(client)("farm")('name -> S)

scala> case class Farm(animals: List[String])
scala> case class Farmer(name: String, age: Long, farm: Farm)

scala> val bunce = for {
     |   _ <- ScanamoAsync.putAll(client)("farm")(List(
     |       Farmer("Boggis", 43L, Farm(List("chicken"))), Farmer("Bunce", 52L, Farm(List("goose"))), Farmer("Bean", 55L, Farm(List("turkey")))
     |     ))
     |   farmer <- ScanamoAsync.get[Farmer](client)("farm")('name -> "Bunce")
     | } yield farmer
     
scala> concurrent.Await.result(bunce, 5.seconds)
res1: Option[cats.data.ValidatedNel[DynamoReadError, Farmer]] = Some(Valid(Farmer(Bunce,52,Farm(List(goose)))))
```

If you want to take a more pure functional approach and push the IO to the edge of your program, you can make 
use of the underlying [Free](http://typelevel.org/cats/tut/freemonad.html) structure:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._

scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val farmersTableResult = LocalDynamoDB.createTable(client)("free")('name -> S)

scala> case class Free(name: String, number: Int)
scala> val operations = for {
     |      _           <- ScanamoFree.putAll("free")(List(Free("Monad", 1), Free("Applicative", 2), Free("Love", 3)))
     |      maybeMonad  <- ScanamoFree.get[Free]("free")('name -> "Monad")
     |      monad       = maybeMonad.flatMap(_.toOption).getOrElse(Free("oops", 9))
     |      _           <- ScanamoFree.put("free")(monad.copy(number = monad.number * 10))
     |      results     <- ScanamoFree.getAll[Free]("free")('name -> List("Monad", "Applicative"))
     | } yield results
     
scala> Scanamo.exec(client)(operations).toList
res1: List[cats.data.ValidatedNel[DynamoReadError, Free]] = List(Valid(Free(Monad,10)), Valid(Free(Applicative,2)))
```

For more details see the [API docs](http://guardian.github.io/scanamo/latest/api/#com.gu.scanamo.Scanamo$)

License
-------

Scanamo is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) (the "License"); 
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.
