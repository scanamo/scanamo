---
layout: docs
title: Scanning
---

### Scanning

If you want to go through all elements of a table, or index, Scanamo 
supports scanning it:

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("lines")('mode -> S, 'line -> S)

case class Transport(mode: String, line: String)
```
```tut:book
val transportTable = Table[Transport]("lines")
val operations = for {
  _ <- transportTable.putAll(Set(
    Transport("Underground", "Circle"),
    Transport("Underground", "Metropolitan"),
    Transport("Underground", "Central"),
    Transport("Tram", "Croydon Tramlink")
  ))
  allLines <- transportTable.scan()
} yield allLines.toList

Scanamo.exec(client)(operations)
```

