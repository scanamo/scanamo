Scanamo [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11) [![Build Status](https://travis-ci.org/guardian/scanamo.svg?branch=master)](https://travis-ci.org/guardian/scanamo) [![Coverage Status](https://coveralls.io/repos/github/guardian/scanamo/badge.svg?branch=master)](https://coveralls.io/github/guardian/scanamo?branch=master)
=======

[![Join the chat at https://gitter.im/guardian/scanamo](https://badges.gitter.im/guardian/scanamo.svg)](https://gitter.im/guardian/scanamo?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala 
simpler and less error-prone.

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Installation
------------

```scala
libraryDependencies ++= Seq(
  "com.gu" %% "scanamo" % "0.4.0"
)
```

Usage
-----

If used the Java SDK to access Dynamo, the most familiar way to use Scanamo 
is via the [Scanamo](http://guardian.github.io/scanamo/latest/api/#com.gu.scanamo.Scanamo$)
object:

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
res1: Option[cats.data.Xor[error.DynamoReadError, Farmer]] = Some(Right(Farmer(McDonald,156,Farm(List(sheep, cow)))))
```

Scanamo also provides a [Table](http://guardian.github.io/scanamo/latest/api/#com.gu.scanamo.Table) 
abstraction:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._

scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val farmersTableResult = LocalDynamoDB.createTable(client)("winners")('name -> S)

scala> case class LuckyWinner(name: String, shape: String)
scala> def temptWithGum(child: LuckyWinner): LuckyWinner = child match {
     |   case LuckyWinner("Violet", _) => LuckyWinner("Violet", "blueberry")
     |   case winner => winner
     | }
scala> val luckyWinners = Table[LuckyWinner]("winners")
scala> val operations = for {
     |      _               <- luckyWinners.putAll(
     |                           List(LuckyWinner("Violet", "human"), LuckyWinner("Augustus", "human"), LuckyWinner("Charlie", "human")))
     |      winners         <- luckyWinners.scan()
     |      winnerList      =  winners.flatMap(_.toOption).toList
     |      temptedWinners  =  winnerList.map(temptWithGum)
     |      _               <- luckyWinners.putAll(temptedWinners)
     |      results         <- luckyWinners.getAll('name -> List("Charlie", "Violet"))
     | } yield results
     
scala> Scanamo.exec(client)(operations).toList
res1: List[cats.data.Xor[error.DynamoReadError, LuckyWinner]] = List(Right(LuckyWinner(Charlie,human)), Right(LuckyWinner(Violet,blueberry)))
```

Note that no operations are actually executed against DynamoDB until `exec` is called. 

It's also possible to make more complex queries:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._
 
scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val transportTableResult = LocalDynamoDB.createTable(client)("transports")('mode -> S, 'line -> S)
scala> case class Transport(mode: String, line: String)
scala> val transportTable = Table[Transport]("transports")
scala> val operations = for {
     |   _ <- transportTable.putAll(List(
     |          Transport("Underground", "Circle"),
     |          Transport("Underground", "Metropolitan"),
     |          Transport("Underground", "Central")
     |     ))
     |   tubesStartingWithC <- transportTable.query('mode -> "Underground" and ('line beginsWith "C"))
     | } yield tubesStartingWithC.toList
     
scala> Scanamo.exec(client)(operations)
res1: List[cats.data.Xor[error.DynamoReadError, Transport]] = List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
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
scala> val farmTable = Table[Farmer]("farm")
scala> val ops = for {
     |   _ <- farmTable.putAll(List(
     |          Farmer("Boggis", 43L, Farm(List("chicken"))), 
     |          Farmer("Bunce", 52L, Farm(List("goose"))), 
     |          Farmer("Bean", 55L, Farm(List("turkey")))
     |        ))
     |   bunce <- farmTable.get('name -> "Bunce")
     | } yield bunce
     
scala> concurrent.Await.result(ScanamoAsync.exec(client)(ops), 5.seconds)
res1: Option[cats.data.Xor[error.DynamoReadError, Farmer]] = Some(Right(Farmer(Bunce,52,Farm(List(goose)))))
```

### Custom Formats

To define a serialisation format for types which Scanamo doesn't already provide:
  
```scala
scala> import org.joda.time._

scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._

scala> case class Foo(dateTime: DateTime)

scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val fooTableResult = LocalDynamoDB.createTable(client)("foo")('dateTime -> S)
 
scala> implicit val jodaStringFormat = DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](
     |   DateTime.parse(_).withZone(DateTimeZone.UTC)
     | )(
     |   _.toString
     | )
scala> val fooTable = Table[Foo]("foo")
scala> val operations = for {
     |      _           <- fooTable.put(Foo(new DateTime(0)))
     |      results     <- fooTable.scan()
     | } yield results
 
scala> Scanamo.exec(client)(operations).toList
res1: List[cats.data.Xor[error.DynamoReadError, Foo]] = List(Right(Foo(1970-01-01T00:00:00.000Z)))
```


License
-------

Scanamo is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) (the "License"); 
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.
