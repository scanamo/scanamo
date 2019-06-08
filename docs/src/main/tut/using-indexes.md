---
layout: docs
title: Using Indexes
position: 5
---

## Using Indexes

Scanamo supports scanning and querying [global secondary indexes](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/GSI.html). 
In the following example, we create and use a table called `transport` with a hash key 
of `mode` and range key of `line` and a global secondary called `colour-index` 
with only a hash key on the `colour` attribute:

```tut:silent
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.auto._

case class Transport(mode: String, line: String, colour: String)
val transport = Table[Transport]("transport")
val colourIndex = transport.index("colour-index")

val client = LocalDynamoDB.client()
val scanamo = Scanamo(client)
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
```
```tut:book
LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")("mode" -> S, "line" -> S)("colour" -> S) {
  val operations = for {
    _ <- transport.putAll(Set(
      Transport("Underground", "Circle", "Yellow"),
      Transport("Underground", "Metropolitan", "Maroon"),
      Transport("Underground", "Central", "Red")))
    maroonLine <- colourIndex.query("colour" -> "Maroon")
  } yield maroonLine.toList
  scanamo.exec(operations)
}
```