---
layout: default
title:  "Scanamo"
---
Scanamo is a library for more simply reading and writing to [DynamoDB](https://aws.amazon.com/documentation/dynamodb/)
from Scala. Currently it just handles reading and write values to [AttributeValue](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/model/AttributeValue.html)

It does this for simple types:

```tut:silent
import com.gu.scanamo.DynamoFormat
```
```tut
val av = DynamoFormat[String].write("blah")
DynamoFormat[String].read(av)
```

It also uses Shapeless to provide serialisation of case classes:

```tut:silent
case class Foo(a: String, b: List[Long])
val fooFormat = DynamoFormat[Foo]
```
```tut
val fooAsAv = fooFormat.write(Foo("abc", List(1L, 2L, 3L)))
fooFormat.read(fooAsAv)
```