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
 
### Put and Get

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

### Delete

To remove an item in it's entirety, we can use delete:

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("villains")('name -> S)

case class Villain(name: String, catchphrase: String)
```
```tut:book
val villains = Table[Villain]("villains")
val operations = for {
  _ <- villains.put(Villain("Dalek", "EXTERMINATE!"))
  _ <- villains.put(Villain("Cyberman", "DELETE"))
  _ <- villains.delete('name -> "Cyberman")
  survivors <- villains.scan()
} yield survivors
     
Scanamo.exec(client)(operations)
```

### Update

If you want to change some of the fields of an item, that don't form part of it's key,
 without replacing the item entirely, you can use the `update` operation:

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("teams")('name -> S)
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

Which fields are updated can be based on incoming data:

```tut:silent
import cats.data.NonEmptyList
import cats.implicits._
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.error.DynamoReadError

LocalDynamoDB.createTable(client)("favourites")('name -> S)
case class Favourites(name: String, colour: String, number: Long)
val favouritesTable = Table[Favourites]("favourites")

Scanamo.exec(client)(favouritesTable.put(Favourites("Alice", "Blue", 42L)))

case class FavouriteUpdate(name: String, colour: Option[String], number: Option[Long])
def updateFavourite(fu: FavouriteUpdate): Option[ScanamoOps[Either[DynamoReadError, Favourites]]] = {
  val updates = List(
    fu.colour.map(c => set('colour -> c)), 
    fu.number.map(n => set('number -> n))
  )
  NonEmptyList.fromList(updates.flatten).map(ups =>
    favouritesTable.update('name -> fu.name, ups.reduce)
  )
}
```
```tut:book
val updates = List(
  FavouriteUpdate("Alice", Some("Aquamarine"), Some(93L)),
  FavouriteUpdate("Alice", Some("Red"), None),
  FavouriteUpdate("Alice", None, None)
)

Scanamo.exec(client)(
  for {
    _ <- updates.flatMap(updateFavourite).sequence
    result <- favouritesTable.get('name -> "Alice")
  } yield result
)

```

Further examples, showcasing different types of update can be found in the 
[scaladoc for the `update` method on `Table`](/scanamo/latest/api/com/gu/scanamo/Table.html#update(key:com.gu.scanamo.query.UniqueKey[_],expression:com.gu.scanamo.update.UpdateExpression):com.gu.scanamo.ops.ScanamoOps[Either[com.gu.scanamo.error.DynamoReadError,V]]) 

### Scan

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

### Query

Scanamo can be used to perform most queries that can be made against DynamoDB

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._

val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
LocalDynamoDB.createTable(client)("transports")('mode -> S, 'line -> S)

case class Transport(mode: String, line: String)
val transportTable = Table[Transport]("transports")
val operations = for {
  _ <- transportTable.putAll(Set(
    Transport("Underground", "Circle"),
    Transport("Underground", "Metropolitan"),
    Transport("Underground", "Central")
  ))
  tubesStartingWithC <- transportTable.query('mode -> "Underground" and ('line beginsWith "C"))
} yield tubesStartingWithC.toList
```
```tut:book
Scanamo.exec(client)(operations)
```


