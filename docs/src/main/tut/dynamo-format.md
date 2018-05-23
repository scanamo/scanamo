---
layout: docs
title: DynamoFormat
position:  6
---

## DynamoFormat

Scanamo uses the [`DynamoFormat`](latest/api/com/gu/scanamo/DynamoFormat.html) 
type class to define how to read and write different types to DynamoDB.

Many common types have a `DynamoFormat` provided by Scanamo. For a full list see
of those supported, you can look at the [companion object](latest/api/com/gu/scanamo/DynamoFormat$.html).

Scanamo also supports semi- and fully automatic derivation of formats for case 
classes and sealed trait families where all the contained types have a defined 
or derivable `DynamoFormat`.

### Semi-automatic derivation

As of v???, Scanamo now supports semi-automatic derivation. It's simple, you need 
to specify which of your data types have to be derived by calling 
`com.gu.scanamo.generic.semiauto.deriveDynamoFormat`. It is customary, though not 
a rule at all, to put these in the companion object of your data type.

For example,

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("forecasts")('location -> S, 'weather -> S)
```
```tut:book
import com.gu.scanamo.generic.semiauto._

sealed trait Weather
case object Sunny extends Weather
case object Foggy extends Weather
case object Rainy extends Weather
case object Londony extends Weather

case class Forecast(location: String, weather: Weather)

implicit val formatW: DynamoFormat[Weather] = deriveDynamoFormat
implicit val formatF: DynamoFormat[Forecast] = deriveDynamoFormat

val forecastTable = Table[Forecast]("forecasts")
val operations = for {
  _           <- forecastTable.put(Forecast("London", Londony))
  results     <- forecastTable.scan()
} yield results
 
Scanamo.exec(client)(operations).toList
```

### Fully automatic derivation

Before v???, automatic derivation was the only option available. It is still there,
but you will now need to bring the relevant machinery into scope:

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("gremlins")('name -> S, 'state -> S)
```
```tut:book
import com.gu.scanamo.generic.auto._

sealed trait State
case object Dry extends State
case object Wet extends State

case class Gremlin(name: String, state: State)

val gremlinTable = Table[Gremlin]("gremlins")
val operations = for {
  _           <- gremlinTable.put(Gremlin("Gizmo", Dry))
  _           <- gremlinTable.put(Gremlin("Stripe", Wet))
  results     <- gremlinTable.scan()
} yield results
 
Scanamo.exec(client)(operations).toList
```

### Custom Formats

It's also possible to define a serialisation format for types which Scanamo 
doesn't already support and can't derive. Normally this involves using the 
[xmap](latest/api/com/gu/scanamo/DynamoFormat$.html#xmap[A,B](r:B=>Either[com.gu.scanamo.error.DynamoReadError,A])(w:A=>B)(implicitf:com.gu.scanamo.DynamoFormat[B]):com.gu.scanamo.DynamoFormat[A]) 
or [coercedXmap](latest/api/com/gu/scanamo/DynamoFormat$.html#coercedXmap[A,B,T>:Null<:Throwable](read:B=>A)(write:A=>B)(implicitf:com.gu.scanamo.DynamoFormat[B],implicitT:scala.reflect.ClassTag[T],implicitNT:cats.NotNull[T]):com.gu.scanamo.DynamoFormat[A])
to translate between the type and one Scanamo does already know about.

For example, to store Joda `DateTime` objects as ISO `String`s in Dynamo:
  
```tut:silent
import org.joda.time._

import com.gu.scanamo._
import com.gu.scanamo.syntax._
import com.gu.scanamo.generic.auto._

case class Foo(dateTime: DateTime)

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("foo")('dateTime -> S)
```
```tut:book
implicit val jodaStringFormat = DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](
  DateTime.parse(_).withZone(DateTimeZone.UTC)
)(
  _.toString
)
val fooTable = Table[Foo]("foo")
val operations = for {
  _           <- fooTable.put(Foo(new DateTime(0)))
  results     <- fooTable.scan()
} yield results
 
Scanamo.exec(client)(operations).toList
```

### Formats for Refined Types

Scanamo supports Scala refined types via the `scanamo-refined` module, helping you to define custom formats
for types built using the predicates provided by the [refined](https://github.com/fthomas/refined) project.
Refined types give an extra layer of type safety to our programs making the compilation fail when we try to
assign wrong values to them.

To use them in your project you will need to include the dependency in your project:

```
libraryDependencies += "com.gu.scanamo" %% "scanamo-refined" % "x.y.z"
```

And then import the support for refined types and define your model:

```tut:silent
import com.gu.scanamo.refined._
import com.gu.scanamo.generic.auto._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._

type PosInt = Int Refined Positive

case class Customer(age: PosInt)
LocalDynamoDB.createTable(client)("Customer")('age -> N)
```

You just now use it like if the type `PosInt` was natively supported by `scanamo`:

```tut:book
val customerTable = Table[Customer]("Customer")
val operations = for {
  _       <- customerTable.put(Customer(67))
  results <- customerTable.scan()
} yield results

Scanamo.exec(client)(operations).toList
```
