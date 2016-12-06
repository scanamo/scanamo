---
layout: docs
title: Updating
position: 4
---

### Updating

If you want to update some of the fields of a row, which don't form part of the key,
 without replacing it entirely, you can use the `update` operation:

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
val teamTableResult = LocalDynamoDB.createTable(client)("teams")('name -> S)
case class Team(name: String, goals: Int, scorers: List[String], mascot: Option[String])
val teamTable = Table[Team]("teams")
val operations = for {
  _ <- teamTable.put(Team("Watford", 1, List("Blissett"), Some("Harry the Hornet")))
  updated <- teamTable.update('name -> "Watford", 
    set('goals -> 2) and append('scorers -> "Barnes") and remove('mascot))
} yield updated
```
```tut:book
Scanamo.exec(client)(operations)
```