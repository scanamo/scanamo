---
layout: docs
title: DynamoFormat
position:  2
---

### DynamoFormat

Scanamo uses the [DynamoFormat](latest/api/com/gu/scanamo/DynamoFormat.html) 
type class to define how to read and write different types to DynamoDB.

Many common types have a `DynamoFormat` provided by Scanamo. For a full list see
of those supported, you can look at the [companion object](latest/api/com/gu/scanamo/DynamoFormat$.html)

Scanamo also supports automatically deriving formats for case classes and 
sealed trait families where all the contained types have a defined or derivable
`DynamoFormat`.

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