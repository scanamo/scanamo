### Querying

It's also possible to make more complex queries:

```tut
import com.gu.scanamo._
import com.gu.scanamo.syntax._
 
val client = LocalDynamoDB.client()
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
val transportTableResult = LocalDynamoDB.createTable(client)("transports")('mode -> S, 'line -> S)
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
     
Scanamo.exec(client)(operations)
```