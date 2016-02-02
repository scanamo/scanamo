---
layout: default
title:  "Scanamo"
---
Scanamo is a library for more simply reading and writing to [DynamoDB](https://aws.amazon.com/documentation/dynamodb/)
from Scala. Currently it just handles reading and write values to [AttributeValue](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/model/AttributeValue.html)

It does this for simple types:

```scala
import com.gu.scanamo.DynamoFormat
```
```scala
scala> val av = DynamoFormat[String].write("blah")
av: com.amazonaws.services.dynamodbv2.model.AttributeValue = {S: blah,}

scala> DynamoFormat[String].read(av)
res0: String = blah
```

It also uses Shapeless to provide serialisation of case classes:

```scala
case class Foo(a: String, b: List[Long])
val fooFormat = DynamoFormat[Foo]
```
```scala
scala> val fooAsAv = fooFormat.write(Foo("abc", List(1L, 2L, 3L)))
fooAsAv: com.amazonaws.services.dynamodbv2.model.AttributeValue = {M: {a={S: abc,}, b={L: [{N: 1,}, {N: 2,}, {N: 3,}],}},}

scala> fooFormat.read(fooAsAv)
res1: Foo = Foo(abc,List(1, 2, 3))
```
