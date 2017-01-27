---
layout: docs
title: Asynchronous Requests
position: 5
---

## Non-blocking requests
 
Whilst for simplicity most examples in these documents are based on synchronous
requests to DynamoDB, Scanamo supports making the requests asynchronously with
a client that implements the `AmazonDynamoDBAsync` interface:

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
 
val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("farm")('name -> S)

case class Farm(animals: List[String])
case class Farmer(name: String, age: Long, farm: Farm)
val farmTable = Table[Farmer]("farm")
val ops = for {
  _ <- farmTable.putAll(Set(
    Farmer("Boggis", 43L, Farm(List("chicken"))),
    Farmer("Bunce", 52L, Farm(List("goose"))),
    Farmer("Bean", 55L, Farm(List("turkey")))
  ))
  bunce <- farmTable.get('name -> "Bunce")
} yield bunce
```
```tut:book
//concurrent.Await.result(ScanamoAsync.exec(client)(ops), 5.seconds)
```

Note that `AmazonDynamoDBAsyncClient` uses a thread pool internally.