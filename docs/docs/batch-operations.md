---
layout: docs
title: Batch Operations
position: 2
---

## Batch Operations
 
Many operations against Dynamo can be performed in batches. Scanamo
has support for putting, getting and deleting in batches

```scala mdoc:silent
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
 
val client = LocalDynamoDB.syncClient()
val scanamo = Scanamo(client)

LocalDynamoDB.createTable(client)("lemmings")("role" -> S)

case class Lemming(role: String, number: Long)
```

```scala mdoc
val lemmingsTable = Table[Lemming]("lemmings")
val ops = for {
  _ <- lemmingsTable.putAll(Set(
    Lemming("Walker", 99), Lemming("Blocker", 42), Lemming("Builder", 180)
  ))
  bLemmings <- lemmingsTable.getAll("role" -> Set("Blocker", "Builder"))
  _ <- lemmingsTable.deleteAll("role" -> Set("Walker", "Blocker"))
  survivors <- lemmingsTable.scan()
} yield (bLemmings, survivors)
val (bLemmings, survivors) = scanamo.exec(ops)
bLemmings.flatMap(_.toOption)
survivors.flatMap(_.toOption)
```

```scala mdoc:invisible
LocalDynamoDB.deleteTable(client)("lemmings")
```