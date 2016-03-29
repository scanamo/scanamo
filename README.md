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
  "com.gu" %% "scanamo" % "0.1.0"
)
```

Usage
-----

You can simply `put` case classes to and `get` them back from DynamoDB:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._
 
scala> val client = LocalDynamoDB.client()
scala> case class Farm(animals: List[String])
scala> case class Farmer(name: String, age: Long, farm: Farm)

scala> Scanamo.put(client)("farmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
scala> Scanamo.get[Farmer](client)("farmers")('name -> "McDonald")
res0: Some(Valid(Farmer(McDonald,156,Farm(List(sheep, cow)))))
```

It's also possible to make more complex queries:

```scala
scala> import com.gu.scanamo._
scala> import com.gu.scanamo.syntax._
 
scala> val client = LocalDynamoDB.client()
scala> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
scala> val transportTableResult = LocalDynamoDB.createTable(client)("transport")('mode -> S, 'line -> S)
scala> case class Transport(mode: String, line: String)

scala> val lines = Scanamo.putAll(client)("transport")(List(
     |       Transport("Underground", "Circle"),
     |       Transport("Underground", "Metropolitan"),
     |       Transport("Underground", "Central")
     | ))
scala> Scanamo.query[Transport](client)("transport")('mode -> "Underground" and ('line beginsWith "C")).toList
res0: List(Valid(Transport(Underground,Central)), Valid(Transport(Underground,Circle)))

scala> val deleteTable = client.deleteTable("transport")
```

For more details see the [API docs](http://guardian.github.io/scanamo/latest/api/#com.gu.scanamo.Scanamo$)

License
-------

Scanamo is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) (the "License"); 
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.
