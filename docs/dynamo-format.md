---
title: DynamoFormat
sidebar_position: 6
---

Scanamo uses the `DynamoFormat` type class to define how to read and write different types to DynamoDB.

Many common types have a `DynamoFormat` provided by Scanamo. For a full list see of those supported, you can look at the companion object.

Scanamo also supports automatically deriving formats for case classes and sealed trait families where all the contained types have a defined or derivable `DynamoFormat`.

### Automatic Derivation

Scanamo can automatically derive `DynamoFormat` for case classes (as long as all its members can also be derived). Ex:

```scala mdoc:silent
import org.scanamo._
import org.scanamo.generic.auto._

case class Farm(animals: List[String])
case class Farmer(name: String, age: Long, farm: Farm)

val table = Table[Farmer]("farmer")
table.putAll(
    Set(
        Farmer("McDonald", 156L, Farm(List("sheep", "cow"))),
        Farmer("Boggis", 43L, Farm(List("chicken")))
    )
)
```

### Semi-automatic Derivation

Scanamo offers a convenient way (semi-automatic) to derive `DynamoFormat` in your code. 
Ex:

```scala mdoc:silent:reset
import org.scanamo._
import org.scanamo.generic.semiauto._

case class Farm(animals: List[String])
case class Farmer(name: String, age: Long, farm: Farm)

implicit val formatFarm: DynamoFormat[Farm] = deriveDynamoFormat
implicit val formatFarmer: DynamoFormat[Farmer] = deriveDynamoFormat
```

### Custom Formats

It's also possible to define a serialisation format for types which Scanamo doesn't already support and can't derive. Normally this involves using the `DynamoFormat.xmap` or `DynamoFormat.coercedXmap` to translate between the type and one Scanamo does already know about.

For example, to store Joda `DateTime` objects as ISO `String`s in Dynamo:

```scala mdoc:reset
import org.joda.time._
import org.scanamo._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._

case class Foo(dateTime: DateTime)

val client = LocalDynamoDB.syncClient()
val scanamo = Scanamo(client)

LocalDynamoDB.createTable(client)("foo")("dateTime" -> S)

implicit val jodaStringFormat = DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException](
  DateTime.parse(_).withZone(DateTimeZone.UTC),
  _.toString
)

val fooTable = Table[Foo]("foo")

scanamo.exec {
  for {
    _           <- fooTable.put(Foo(new DateTime(0)))
    results     <- fooTable.scan()
  } yield results
}.toList
```

### Derived Formats

Scanamo uses [magnolia](https://magnolia.work/opensource/magnolia) and implicit derivation to automatically derive `DynamoFormat`s for case classes and sealed trait families. You may also see or hear sealed trait families referred to as Algebraic Data Types (ADTs) and co-products. Here is an example that could be used to support event sourcing (assuming a table with a partition key of `id` and sort key `seqNo`):

```scala mdoc:silent:reset
import java.util.UUID

import org.scanamo._

// Sealed trait family for events.
sealed trait Event
case class Create(name: String) extends Event
case class Delete(reason: String) extends Event

// An event envelope that wraps events.
case class EventEnvelope(id: UUID, seqNo: Int, event: Event)

// Example instantiations.
val id = UUID.fromString("9e5fd6e9-65ef-472c-ad89-e5fe658f14c6")
val create = EventEnvelope(id, 0, Create("Something"))
val delete = EventEnvelope(id, 1, Delete("Oops"))
```

```scala mdoc
import org.scanamo._
import org.scanamo.generic.auto._

val attributeValue = DynamoFormat[EventEnvelope].write(create)

val dynamoRecord = DynamoFormat[EventEnvelope].read(attributeValue)
```

If you look carefully at the attribute value (pretty-printed below) then you can see that the envelope (or wrapper) is necessary. This is because Scanamo writes the sealed trait family and associated case classes into a map. This allows Scanamo to use the map key to disambiguate between the sub-classes when reading attribute values. This makes sense and works well for most use cases, but it does mean you cannot persist an sealed trait family directly (i.e. without an envelope or wrapper) using automatic derivation because partition keys do not support maps.

Here is the pretty-printed attribute value that Scanamo generates:

```json
{
  "M": {
    "seqNo": {
      "N": 0
    },
    "id": {
      "S": "9e5fd6e9-65ef-472c-ad89-e5fe658f14c6"
    },
    "event": {
      "M": {
        "Create": {
          "M": {
            "name":{
              "S": "Something"
            }
          }
        }
      }
    }
  }
}
```
