---
layout: docs
title: Custom Formats
position:  2
---

### Custom Formats

Scanamo uses the `DynamoFormat` type class to define how to read and write 
different types to DynamoDB. Scanamo provides such formats for many common 
types, but it's also possible to define a serialisation format for types 
which Scanamo doesn't provide. For example to store Joda `DateTime` objects
as ISO `String`s in Dynamo:
  
```tut:silent
import org.joda.time._

import com.gu.scanamo._
import com.gu.scanamo.syntax._

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