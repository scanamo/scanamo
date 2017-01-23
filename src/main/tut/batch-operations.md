---
layout: docs
title: Batch Operations
position: 2
---

### Batch Operations
 
Many operations against Dynamo can be performed in batches. Scanamo
has support for putting, getting and deleting in batches

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
 
val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("lemmings")('role -> S)

case class Lemming(role: String, number: Long)
```

```tut:book
val lemmingsTable = Table[Lemming]("lemmings")
val ops = for {
  _ <- lemmingsTable.putAll(Set(
    Lemming("Walker", 99), Lemming("Blocker", 42), Lemming("Builder", 180)
  ))
  bLemmings <- lemmingsTable.getAll('role -> Set("Blocker", "Builder"))
  _ <- lemmingsTable.deleteAll('role -> Set("Walker", "Blocker"))
  survivors <- lemmingsTable.scan()
} yield (bLemmings, survivors)
val (bLemmings, survivors) = Scanamo.exec(client)(ops)
import cats.syntax.either._
bLemmings.flatMap(_.toOption)
survivors.flatMap(_.toOption)
```