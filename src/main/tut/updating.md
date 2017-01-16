---
layout: docs
title: Updating
---

### Updating

If you want to update some of the fields of a row, which don't form part of the key,
 without replacing it entirely, you can use the `update` operation:

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

You can also conditionally update different elements of the document:

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