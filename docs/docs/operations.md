---
layout: docs
title: Operations 
position: 1
---

## Operations

Scanamo supports all the DynamoDB operations that interact with individual items in DynamoDB tables:

 * [Put](#put-and-get) for adding a new item, or replacing an existing one
 * [Get](#put-and-get) for retrieving an item by a fully specified key
 * [Delete](#delete) for removing an item
 * [Update](#update) for updating some portion of the fields of an item, whilst leaving the rest 
 as is
 * [Scan](#scan) for retrieving all elements of a table
 * [Query](#query) for retrieving all elements with a given hash-key and a range key that matches
 some criteria
 
Scanamo also supports [batched operations](batch-operations.md), [conditional operations](conditional-operations.md) 
and queries against [secondary indexes](using-indexes.md).
 
### Put and Get

Often when using DynamoDB, the primary use case is simply putting objects into 
Dynamo and subsequently retrieving them:

```scala mdoc:silent
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._

val client = LocalDynamoDB.syncClient()
val scanamo = Scanamo(client)
LocalDynamoDB.createTable(client)("muppets")("name" -> S)

case class Muppet(name: String, species: String)
```
```scala mdoc
val muppets = Table[Muppet]("muppets")
scanamo.exec {
  for {
    _ <- muppets.put(Muppet("Kermit", "Frog"))
    _ <- muppets.put(Muppet("Cookie Monster", "Monster"))
    _ <- muppets.put(Muppet("Miss Piggy", "Pig"))
    kermit <- muppets.get("name" -> "Kermit")
  } yield kermit
}
```

Note that when using `Table` no operations are actually executed against DynamoDB until `exec` is called. 

### Delete

To remove an item in its entirety, we can use delete:

```scala mdoc:silent
import org.scanamo._
import org.scanamo.syntax._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._

LocalDynamoDB.createTable(client)("villains")("name" -> S)

case class Villain(name: String, catchphrase: String)
```
```scala mdoc
val villains = Table[Villain]("villains")
scanamo.exec {
  for {
    _ <- villains.put(Villain("Dalek", "EXTERMINATE!"))
    _ <- villains.put(Villain("Cyberman", "DELETE"))
    _ <- villains.delete("name" -> "Cyberman")
    survivors <- villains.scan()
  } yield survivors
}
```

### Update

If you want to change some of the fields of an item, that don't form part of its key,
 without replacing the item entirely, you can use the `update` operation:

```scala mdoc:silent
import org.scanamo._
import org.scanamo.syntax._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._

LocalDynamoDB.createTable(client)("teams")("name" -> S)

case class Team(name: String, goals: Int, scorers: List[String], mascot: Option[String])
```

```scala mdoc
val teamTable = Table[Team]("teams")
scanamo.exec {
  for {
    _ <- teamTable.put(Team("Watford", 1, List("Blissett"), Some("Harry the Hornet")))
    updated <- teamTable.update("name" -> "Watford", 
      set("goals" -> 2) and append("scorers" -> "Barnes") and remove("mascot"))
  } yield updated
}
```

Which fields are updated can be based on incoming data:

```scala mdoc:silent
import cats.data.NonEmptyList
import org.scanamo.ops.ScanamoOps
import org.scanamo.DynamoReadError
import org.scanamo.update.UpdateExpression

LocalDynamoDB.createTable(client)("favourites")("name" -> S)

case class Favourites(name: String, colour: String, number: Long)
```

```scala mdoc
val favouritesTable = Table[Favourites]("favourites")

scanamo.exec(favouritesTable.put(Favourites("Alice", "Blue", 42L)))

case class FavouriteUpdate(name: String, colour: Option[String], number: Option[Long])

def updateFavourite(fu: FavouriteUpdate): Option[ScanamoOps[Either[DynamoReadError, Favourites]]] = {
  val updates: List[UpdateExpression] = List(
    fu.colour.map(c => set("colour" -> c)), 
    fu.number.map(n => set("number" -> n))
  ).flatten
  NonEmptyList.fromList(updates).map(ups =>
    favouritesTable.update("name" -> fu.name, ups.reduce[UpdateExpression](_ and _))
  )
}
```
```scala mdoc
import cats.implicits._

val updates = List(
  FavouriteUpdate("Alice", Some("Aquamarine"), Some(93L)),
  FavouriteUpdate("Alice", Some("Red"), None),
  FavouriteUpdate("Alice", None, None)
)

scanamo.exec(
  for {
    _ <- updates.flatMap(updateFavourite).sequence
    result <- favouritesTable.get("name" -> "Alice")
  } yield result
)

```

Further examples, showcasing different types of update can be found in the scaladoc for the `update` method on `Table`.

### Scan

If you want to go through all elements of a table, or index, Scanamo 
supports scanning it:

```scala mdoc:silent
import org.scanamo._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._

LocalDynamoDB.createTable(client)("lines")("mode" -> S, "line" -> S)

case class Transport(mode: String, line: String)
```
```scala mdoc
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

scanamo.exec(operations)
```

### Query

Scanamo can be used to perform most queries that can be made against DynamoDB

```scala mdoc
scanamo.exec {
  for {
    _ <- transportTable.putAll(Set(
      Transport("Underground", "Circle"),
      Transport("Underground", "Metropolitan"),
      Transport("Underground", "Central")
    ))
    tubesStartingWithC <- transportTable.query("mode" -> "Underground" and ("line" beginsWith "C"))
  } yield tubesStartingWithC.toList
}
```


```scala mdoc:invisible
LocalDynamoDB.deleteTable(client)("muppets")
LocalDynamoDB.deleteTable(client)("villains")
LocalDynamoDB.deleteTable(client)("teams")
LocalDynamoDB.deleteTable(client)("favourites")
LocalDynamoDB.deleteTable(client)("lines")
```