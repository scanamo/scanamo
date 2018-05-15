---
layout: docs
title: Conditional Operations
position: 3
---

## Conditional Operations

Modifying operations ([Put](operations.html#put-and-get), [Delete](operations.html#delete),
[Update](operations.html#update)) can be performed conditionally, so that they
only have an effect if some state of the DynamoDB table is true at the time of 
execution.

```tut:silent
import com.gu.scanamo._
import com.gu.scanamo.syntax._
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
val client = LocalDynamoDB.client()
case class Gremlin(number: Int, name: String, wet: Boolean, friendly: Boolean)
implicit val format: DynamoFormat[Gremlin] = DerivedDynamoFormat.derive
```
```tut:book
val gremlinsTable = Table[Gremlin]("gremlins")
LocalDynamoDB.withTable(client)("gremlins")('number -> N) {
  val ops = for {
    _ <- gremlinsTable.putAll(
      Set(Gremlin(1, "Gizmo", false, true), Gremlin(2, "George", true, false)))
    // Only `put` Gremlins if not already one with the same number
    _ <- gremlinsTable.given(not(attributeExists('number)))
      .put(Gremlin(2, "Stripe", false, true))
    _ <- gremlinsTable.given(not(attributeExists('number)))
      .put(Gremlin(3, "Greta", true, true))
    allGremlins <- gremlinsTable.scan()  
    _ <- gremlinsTable.given('wet -> true)
      .delete('number -> 1)
    _ <- gremlinsTable.given('wet -> true)
      .delete('number -> 2)
    _ <- gremlinsTable.given('wet -> true)
      .update('number -> 1, set('friendly -> false))
    _ <- gremlinsTable.given('wet -> true)
      .update('number -> 3, set('friendly -> false))
    remainingGremlins <- gremlinsTable.scan()
  } yield (allGremlins, remainingGremlins)
  Scanamo.exec(client)(ops)
}
```

More examples can be found in the [Table ScalaDoc](/latest/api/com/gu/scanamo/Table.html#given[T](condition:T)(implicitevidence$2:com.gu.scanamo.query.ConditionExpression[T]):com.gu.scanamo.query.ConditionalOperation[V,T]).