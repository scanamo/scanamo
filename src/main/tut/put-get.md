---
layout: docs
title: Putting and Getting
---

### Putting and Getting

Often when using DynamoDB, the primary use case is simply putting objects into 
Dynamo and subsequently retrieving them:

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("muppets")('name -> S)

case class Muppet(name: String, species: String)
```
```tut:book
val muppets = Table[Muppet]("muppets")
val operations = for {
  _ <- muppets.put(Muppet("Kermit", "Frog"))
  _ <- muppets.put(Muppet("Cookie Monster", "Monster"))
  _ <- muppets.put(Muppet("Miss Piggy", "Pig"))
  kermit <- muppets.get('name -> "Kermit")
} yield kermit
     
Scanamo.exec(client)(operations)
```

Note that when using `Table` no operations are actually executed against DynamoDB until `exec` is called. 