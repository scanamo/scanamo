Scanamo [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu/scanamo_2.11) [![Build Status](https://travis-ci.org/guardian/scanamo.svg?branch=master)](https://travis-ci.org/guardian/scanamo) [![Coverage Status](https://coveralls.io/repos/github/guardian/scanamo/badge.svg?branch=master)](https://coveralls.io/github/guardian/scanamo?branch=master) [![Chat on gitter](https://badges.gitter.im/guardian/scanamo.svg)](https://gitter.im/guardian/scanamo)
=======

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala 
simpler and less error-prone.

The main focus is on making it easier to avoid mistakes and typos by leveraging Scala's type system and some
higher level abstractions.

Installation
------------

```scala
libraryDependencies ++= Seq(
  "com.gu" %% "scanamo" % "0.6.0"
)
```

Usage
-----

 - [Putting and Getting](#putting-and-getting)
 - [Querying](#querying)
 - [Updating](#updating)
 - [Using Indexes](#using-indexes)
 - [Non-blocking requests](#non-blocking-requests)
 - [Custom formats](#custom-formats)

### Putting and Getting

If you've used the Java SDK to access Dynamo, the most familiar way to use Scanamo 
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

The `Xor` represents the possibility that an item might exist, but not be parseable into the given 
type, in this case `Farmer`. For more information on `Xor`, see the 
[Cats documentation](http://typelevel.org/cats/tut/xor.html).

Like all the examples in this README and the Scaladoc, this creates a table, so that it 
can be checked using [sbt-doctest](https://github.com/tkawachi/sbt-doctest), but the same 
operations can happily run against pre-existing tables.

### Table

Scanamo provides a [Table](http://guardian.github.io/scanamo/latest/api/#com.gu.scanamo.Table) 
abstraction to reduce noise when defining multiple operations against the same table:

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
     |                           Set(LuckyWinner("Violet", "human"), LuckyWinner("Augustus", "human"), LuckyWinner("Charlie", "human")))
     |      winners         <- luckyWinners.scan()
     |      winnerList      =  winners.flatMap(_.toOption).toList
     |      temptedWinners  =  winnerList.map(temptWithGum)
     |      _               <- luckyWinners.putAll(temptedWinners.toSet)
     |      results         <- luckyWinners.getAll('name -> Set("Charlie", "Violet"))
     | } yield results
     
scala> Scanamo.exec(client)(operations)
res1: Set[cats.data.Xor[error.DynamoReadError, LuckyWinner]] = Set(Right(LuckyWinner(Charlie,human)), Right(LuckyWinner(Violet,blueberry)))
```

Note that when using `Table` no operations are actually executed against DynamoDB until `exec` is called. 

### Querying

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
     |   _ <- transportTable.putAll(Set(
     |          Transport("Underground", "Circle"),
     |          Transport("Underground", "Metropolitan"),
     |          Transport("Underground", "Central")
     |     ))
     |   tubesStartingWithC <- transportTable.query('mode -> "Underground" and ('line beginsWith "C"))
     | } yield tubesStartingWithC.toList
     
scala> Scanamo.exec(client)(operations)
res1: List[cats.data.Xor[error.DynamoReadError, Transport]] = List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
```

### Updating

If you want to update some of the fields of a row, which don't form part of the key, 
 without replacing it entirely, you can use the `update` operation:
 
```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._
 
scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val teamTableResult = LocalDynamoDB.createTable(client)("teams")('name -> S)
scala> case class Team(name: String, goals: Int, scorers: List[String])
scala> val teamTable = Table[Team]("teams")
scala> val operations = for {
     |   _ <- teamTable.put(Team("Watford", 1, List("Blissett")))
     |   _ <- teamTable.update('name -> "Watford", set('goals -> 2) and append('scorers -> "Barnes"))
     |   watford <- teamTable.get('name -> "Watford")
     | } yield watford
     
scala> Scanamo.exec(client)(operations)
res1: Option[cats.data.Xor[error.DynamoReadError, Team]] = Some(Right(Team(Watford,2,List(Blissett, Barnes))))
``` 

### Using Indexes

You can also scan and query indexes with Scanamo. In the following example, there is a
table called `transport` with a hash key of `mode` and range key of `line` and a 
[global secondary index](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/GSI.html) 
called `colour-index` with only a hash key on the `colour` attribute:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._

scala> case class Transport(mode: String, line: String, colour: String)
scala> val transport = Table[Transport]("transport")
scala> val colourIndex = transport.index("colour-index")

scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")('mode -> S, 'line -> S)('colour -> S) {
     |   val operations = for {
     |     _ <- transport.putAll(Set(
     |       Transport("Underground", "Circle", "Yellow"),
     |       Transport("Underground", "Metropolitan", "Maroon"),
     |       Transport("Underground", "Central", "Red")))
     |     maroonLine <- colourIndex.query('colour -> "Maroon")
     |   } yield maroonLine.toList
     |   Scanamo.exec(client)(operations)
     | }
res0: List[cats.data.Xor[error.DynamoReadError, Transport]] = List(Right(Transport(Underground,Metropolitan,Maroon)))
```

### Non-blocking requests
 
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
     |   _ <- farmTable.putAll(Set(
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

Scanamo uses the `DynamoFormat` type class to define how to read and write 
different types to DynamoDB. Scanamo provides such formats for many common 
types, but it's also possible to define a serialisation format for types 
which Scanamo doesn't provide. For example to store Joda `DateTime` objects
as ISO `String`s in Dynamo:
  
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
