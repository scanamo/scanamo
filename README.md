Scanamo [![Build Status](https://travis-ci.org/guardian/scanamo.svg?branch=master)](https://travis-ci.org/guardian/scanamo)
=======

Scanamo is a library to make using [DynamoDB](https://aws.amazon.com/documentation/dynamodb/) with Scala case classes simpler.

Assuming you have some DynamoDB `client` in scope, you can use it like:

```scala
scala> case class Farm(animals: List[String])
scala> case class Farmer(name: String, age: Long, farm: Farm)
scala> import com.gu.scanamo.Scanamo
scala> Scanamo.put(client)("farmers")(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
scala> Scanamo.get[String, Farmer](client)("farmers")("name" -> "McDonald")
Some(Valid(Farmer(McDonald,156,Farm(List(sheep, cow)))))
```

For more details see the [API docs](http://guardian.github.io/scanamo/latest/api/#com.gu.scanamo.Scanamo$)

License
-------

Scanamo is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) (the "License"); 
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.
