---
layout: docs
title: Filters
position: 4
---

## Filters

[Scans](operations.html#scan) and [Queries](operations.html#query) can be 
filtered within Dynamo, preventing the memory, network and marshalling 
overhead of filtering on the client.
 
Note that these filters do *not* reduce the [consumed capacity](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ProvisionedThroughput.html) 
in Dynamo. Even though a filter may lead to a small number of results being
returned, it could still exhaust the provisioned capacity or force the 
provisioned capacity to autoscale up to an expensive level.

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._
import com.gu.scanamo.generic.auto._
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
val client = LocalDynamoDB.client()

case class Station(line: String, name: String, zone: Int)
val stationTable = Table[Station]("Station")
```
```tut:book
LocalDynamoDB.withTable(client)("Station")('line -> S, 'name -> S) {
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
        .filter('zone < 8)
        .query('line -> "Metropolitan" and ('name beginsWith "C"))
  } yield filteredStations
  Scanamo.exec(client)(ops)
}
```

More examples can be found in the [Table ScalaDoc](/latest/api/com/gu/scanamo/Table.html#filter[C](condition:C)(implicitevidence$3:com.gu.scanamo.query.ConditionExpression[C]):com.gu.scanamo.TableWithOptions[V]).