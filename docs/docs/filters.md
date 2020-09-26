---
layout: docs
title: Filters
position: 4
---

## Filters

[Scans](operations.md#scan) and [Queries](operations.md#query) can be filtered within Dynamo, preventing the memory, network and marshalling overhead of filtering on the client.
 
Note that these filters do *not* reduce the [consumed capacity](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ProvisionedThroughput.html) in Dynamo. Even though a filter may lead to a small number of results being
returned, it could still exhaust the provisioned capacity or force the provisioned capacity to autoscale up to an expensive level.

```scala mdoc:silent
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
val client = LocalDynamoDB.syncClient()
val scanamo = Scanamo(client)

case class Station(line: String, name: String, zone: Int)
val stationTable = Table[Station]("Station")
```
```scala mdoc
LocalDynamoDB.withTable(client)("Station")("line" -> S, "name" -> S) {
  val ops = for {
    _ <- stationTable.putAll(Set(
      Station("Metropolitan", "Chalfont & Latimer", 8),
      Station("Metropolitan", "Chorleywood", 7),
      Station("Metropolitan", "Rickmansworth", 7),
      Station("Metropolitan", "Croxley", 7),
      Station("Jubilee", "Canons Park", 5)
    ))
    filteredStations <- 
      stationTable
        .filter("zone" < 8)
        .query("line" === "Metropolitan" and ("name" beginsWith "C"))
  } yield filteredStations
  scanamo.exec(ops)
}
```

More examples can be found in the `Table` scaladoc.
