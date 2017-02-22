package com.gu.scanamo

import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query._
import com.gu.scanamo.update.UpdateExpression
import com.amazonaws.services.dynamodbv2.model.QueryRequest

/**
  * Represents a DynamoDB table that operations can be performed against
  *
  * {{{
  * >>> case class Transport(mode: String, line: String)
  * >>> val transport = Table[Transport]("transport")
  *
  * >>> val client = LocalDynamoDB.client()
  * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
  *
  * >>> LocalDynamoDB.withTable(client)("transport")('mode -> S, 'line -> S) {
  * ...   import com.gu.scanamo.syntax._
  * ...   val operations = for {
  * ...     _ <- transport.putAll(Set(
  * ...       Transport("Underground", "Circle"),
  * ...       Transport("Underground", "Metropolitan"),
  * ...       Transport("Underground", "Central")))
  * ...     results <- transport.query('mode -> "Underground" and ('line beginsWith "C"))
  * ...   } yield results.toList
  * ...   Scanamo.exec(client)(operations)
  * ... }
  * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
  * }}}
  */
case class Table[V: DynamoFormat](name: String) {

  def put(v: V) = ScanamoFree.put(name)(v)
  def putAll(vs: Set[V]) = ScanamoFree.putAll(name)(vs)
  def get(key: UniqueKey[_]) = ScanamoFree.get[V](name)(key)
  def getAll(keys: UniqueKeys[_]) = ScanamoFree.getAll[V](name)(keys)
  def delete(key: UniqueKey[_]) = ScanamoFree.delete(name)(key)

  /**
    * Deletes multiple items by a unique key
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    * >>> val farm = Table[Farmer]("farmers")
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> val dataSet = Set(
    * ...   Farmer("Patty", 200L, Farm(List("unicorn"))),
    * ...   Farmer("Ted", 40L, Farm(List("T-Rex"))),
    * ...   Farmer("Jack", 2L, Farm(List("velociraptor"))))
    * >>> LocalDynamoDB.withTable(client)("farmers")('name -> S) {
    * ...   val operations = for {
    * ...     _       <- farm.putAll(dataSet)
    * ...     _       <- farm.deleteAll('name -> dataSet.map(_.name))
    * ...     scanned <- farm.scan
    * ...   } yield scanned.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List()
    * }}}
    */
  def deleteAll(items: UniqueKeys[_]) = ScanamoFree.deleteAll(name)(items)

  /**
    * A secondary index on the table which can be scanned, or queried against
    *
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    * >>> val transport = Table[Transport]("transport")
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")('mode -> S, 'line -> S)('colour -> S) {
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle", "Yellow"),
    * ...       Transport("Underground", "Metropolitan", "Magenta"),
    * ...       Transport("Underground", "Central", "Red")))
    * ...     MagentaLine <- transport.index("colour-index").query('colour -> "Magenta")
    * ...   } yield MagentaLine.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Metropolitan,Magenta)))
    * }}}
    */
  def index(indexName: String) = Index[V](name, indexName)

  /**
    * Updates an attribute that is not part of the key and returns the updated row
    *
    * To set an attribute:
    *
    * {{{
    * >>> case class Forecast(location: String, weather: String)
    * >>> val forecast = Table[Forecast]("forecast")
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTable(client)("forecast")('location -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   val operations = for {
    * ...     _ <- forecast.put(Forecast("London", "Rain"))
    * ...     updated <- forecast.update('location -> "London", set('weather -> "Sun"))
    * ...   } yield updated
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * Right(Forecast(London,Sun))
    * }}}
    *
    * List attributes can also be appended or prepended to:
    *
    * {{{
    * >>> case class Character(name: String, actors: List[String])
    * >>> val characters = Table[Character]("characters")
    *
    * >>> LocalDynamoDB.withTable(client)("characters")('name -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   val operations = for {
    * ...     _ <- characters.put(Character("The Doctor", List("Ecclestone", "Tennant", "Smith")))
    * ...     _ <- characters.update('name -> "The Doctor", append('actors -> "Capaldi"))
    * ...     _ <- characters.update('name -> "The Doctor", prepend('actors -> "McCoy"))
    * ...     results <- characters.scan()
    * ...   } yield results.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Character(The Doctor,List(McCoy, Ecclestone, Tennant, Smith, Capaldi))))
    * }}}
    *
    * Appending or prepending creates the list if it does not yet exist:
    *
    * {{{
    * >>> LocalDynamoDB.withTable(client)("characters")('name -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   val operations = for {
    * ...     _ <- characters.update('name -> "James Bond", append('actors -> "Craig"))
    * ...     results <- characters.query('name -> "James Bond")
    * ...   } yield results.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Character(James Bond,List(Craig))))
    * }}}
    *
    * Multiple operations can also be performed in one call:
    * {{{
    * >>> case class Foo(name: String, bar: Int, l: List[String])
    * >>> val foos = Table[Foo]("foos")
    *
    * >>> LocalDynamoDB.withTable(client)("foos")('name -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   val operations = for {
    * ...     _ <- foos.put(Foo("x", 0, List("First")))
    * ...     updated <- foos.update('name -> "x",
    * ...       append('l -> "Second") and set('bar -> 1))
    * ...   } yield updated
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * Right(Foo(x,1,List(First, Second)))
    * }}}
    *
    * It's also possible to perform `ADD` and `DELETE` updates
    * {{{
    * >>> case class Bar(name: String, counter: Long, set: Set[String])
    * >>> val bars = Table[Bar]("bars")
    *
    * >>> LocalDynamoDB.withTable(client)("bars")('name -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   val operations = for {
    * ...     _ <- bars.put(Bar("x", 1L, Set("First")))
    * ...     _ <- bars.update('name -> "x",
    * ...       add('counter -> 10L) and add('set -> Set("Second")))
    * ...     updatedBar <- bars.update('name -> "x", delete('set -> Set("First")))
    * ...   } yield updatedBar
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * Right(Bar(x,11,Set(Second)))
    * }}}
    *
    * Updates may occur on nested attributes
    * {{{
    * >>> case class Inner(session: String)
    * >>> case class Middle(name: String, counter: Long, inner: Inner, list: List[Int])
    * >>> case class Outer(id: java.util.UUID, middle: Middle)
    * >>> val outers = Table[Outer]("outers")
    *
    * >>> LocalDynamoDB.withTable(client)("outers")('id -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   val id = java.util.UUID.fromString("a8345373-9a93-43be-9bcd-e3682c9197f4")
    * ...   val operations = for {
    * ...     _ <- outers.put(Outer(id, Middle("x", 1L, Inner("alpha"), List(1, 2))))
    * ...     updatedOuter <- outers.update('id -> id,
    * ...       set('middle \ 'inner \ 'session -> "beta") and add(('middle \ 'list)(1) ->  1)
    * ...     )
    * ...   } yield updatedOuter
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * Right(Outer(a8345373-9a93-43be-9bcd-e3682c9197f4,Middle(x,1,Inner(beta),List(1, 3))))
    * }}}
    */
  def update(key: UniqueKey[_], expression: UpdateExpression): ScanamoOps[Either[DynamoReadError, V]] =
    ScanamoFree.update[V](name)(key)(expression)

  /**
    * Query or scan a table, limiting the number of items evaluated by Dynamo
    * {{{
    * >>> case class Transport(mode: String, line: String)
    * >>> val transport = Table[Transport]("transport")
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTable(client)("transport")('mode -> S, 'line -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central")))
    * ...     results <- transport.limit(1).query('mode -> "Underground" and ('line beginsWith "C"))
    * ...   } yield results.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Central)))
    * }}}
    */
  def limit(n: Int) = TableLimit(this, n)

  /**
    * Perform strongly consistent (http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html)
    * read operations against this table. Note that there is no equivalent on
    * table indexes as consistent reads from secondary indexes are not
    * supported by DynamoDB
    *
    * {{{
    * >>> case class City(country: String, name: String)
    * >>> val cityTable = Table[City]("cities")
    *
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    * >>> val (get, scan, query) = LocalDynamoDB.withTable(client)("cities")('country -> S, 'name -> S) {
    * ...   import com.gu.scanamo.syntax._
    * ...   val ops = for {
    * ...     putRes <- cityTable.putAll(Set(
    * ...       City("US", "Nashville"), City("IT", "Rome"), City("IT", "Siena"), City("TZ", "Dar es Salaam")))
    * ...     get <- cityTable.consistently.get('country -> "US" and 'name -> "Nashville")
    * ...     scan <- cityTable.consistently.scan()
    * ...     query <- cityTable.consistently.query('country -> "IT")
    * ...   } yield (get, scan, query)
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * >>> get
    * Some(Right(City(US,Nashville)))
    *
    * >>> scan
    * List(Right(City(US,Nashville)), Right(City(IT,Rome)), Right(City(IT,Siena)), Right(City(TZ,Dar es Salaam)))
    *
    * >>> query
    * List(Right(City(IT,Rome)), Right(City(IT,Siena)))
    * }}}
    */
  def consistently = ConsistentlyReadTable(this)

  /**
    * Performs the chained operation, `put` if the condition is met
    *
    * {{{
    * >>> case class Farm(animals: List[String])
    * >>> case class Farmer(name: String, age: Long, farm: Farm)
    *
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.gu.scanamo.query._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val client = LocalDynamoDB.client()
    *
    * >>> val farmersTable = Table[Farmer]("nursery-farmers")
    * >>> LocalDynamoDB.withTable(client)("nursery-farmers")('name -> S) {
    * ...   val farmerOps = for {
    * ...     _ <- farmersTable.put(Farmer("McDonald", 156L, Farm(List("sheep", "cow"))))
    * ...     _ <- farmersTable.given('age -> 156L).put(Farmer("McDonald", 156L, Farm(List("sheep", "chicken"))))
    * ...     _ <- farmersTable.given('age -> 15L).put(Farmer("McDonald", 156L, Farm(List("gnu", "chicken"))))
    * ...     farmerWithNewStock <- farmersTable.get('name -> "McDonald")
    * ...   } yield farmerWithNewStock
    * ...   Scanamo.exec(client)(farmerOps)
    * ... }
    * Some(Right(Farmer(McDonald,156,Farm(List(sheep, chicken)))))
    *
    * >>> case class Letter(roman: String, greek: String)
    * >>> val lettersTable = Table[Letter]("letters")
    * >>> LocalDynamoDB.withTable(client)("letters")('roman -> S) {
    * ...   val ops = for {
    * ...     _ <- lettersTable.putAll(Set(Letter("a", "alpha"), Letter("b", "beta"), Letter("c", "gammon")))
    * ...     _ <- lettersTable.given('greek beginsWith "ale").put(Letter("a", "aleph"))
    * ...     _ <- lettersTable.given('greek beginsWith "gam").put(Letter("c", "gamma"))
    * ...     letters <- lettersTable.scan()
    * ...   } yield letters
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Letter(b,beta)), Right(Letter(c,gamma)), Right(Letter(a,alpha)))
    *
    * >>> import cats.implicits._
    * >>> case class Turnip(size: Int, description: Option[String])
    * >>> val turnipsTable = Table[Turnip]("turnips")
    * >>> LocalDynamoDB.withTable(client)("turnips")('size -> N) {
    * ...   val ops = for {
    * ...     _ <- turnipsTable.putAll(Set(Turnip(1, None), Turnip(1000, None)))
    * ...     initialTurnips <- turnipsTable.scan()
    * ...     _ <- initialTurnips.flatMap(_.toOption).traverse(t =>
    * ...       turnipsTable.given('size > 500).put(t.copy(description = Some("Big turnip in the country."))))
    * ...     turnips <- turnipsTable.scan()
    * ...   } yield turnips
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Turnip(1,None)), Right(Turnip(1000,Some(Big turnip in the country.))))
    * }}}
    *
    * Conditions can also make use of negation via `not`:
    *
    * {{{
    * >>> case class Thing(a: String, maybe: Option[Int])
    * >>> val thingTable = Table[Thing]("things")
    * >>> LocalDynamoDB.withTable(client)("things")('a -> S) {
    * ...   val ops = for {
    * ...     _ <- thingTable.putAll(Set(Thing("a", None), Thing("b", Some(1)), Thing("c", None)))
    * ...     _ <- thingTable.given(attributeExists('maybe)).put(Thing("a", Some(2)))
    * ...     _ <- thingTable.given(attributeExists('maybe)).put(Thing("b", Some(3)))
    * ...     _ <- thingTable.given(Not(attributeExists('maybe))).put(Thing("c", Some(42)))
    * ...     _ <- thingTable.given(Not(attributeExists('maybe))).put(Thing("b", Some(42)))
    * ...     things <- thingTable.scan()
    * ...   } yield things
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Thing(b,Some(3))), Right(Thing(c,Some(42))), Right(Thing(a,None)))
    * }}}
    *
    * be combined with `and`
    *
    * {{{
    * >>> case class Compound(a: String, maybe: Option[Int])
    * >>> val compoundTable = Table[Compound]("compounds")
    * >>> LocalDynamoDB.withTable(client)("compounds")('a -> S) {
    * ...   val ops = for {
    * ...     _ <- compoundTable.putAll(Set(Compound("alpha", None), Compound("beta", Some(1)), Compound("gamma", None)))
    * ...     _ <- compoundTable.given(attributeExists('maybe) and 'a -> "alpha").put(Compound("alpha", Some(2)))
    * ...     _ <- compoundTable.given(attributeExists('maybe) and 'a -> "beta").put(Compound("beta", Some(3)))
    * ...     _ <- compoundTable.given(Condition('a -> "gamma") and attributeExists('maybe)).put(Compound("gamma", Some(42)))
    * ...     compounds <- compoundTable.scan()
    * ...   } yield compounds
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Compound(beta,Some(3))), Right(Compound(alpha,None)), Right(Compound(gamma,None)))
    * }}}
    *
    * or with `or`
    *
    * {{{
    * >>> case class Choice(number: Int, description: String)
    * >>> val choicesTable = Table[Choice]("choices")
    * >>> LocalDynamoDB.withTable(client)("choices")('number -> N) {
    * ...   val ops = for {
    * ...     _ <- choicesTable.putAll(Set(Choice(1, "cake"), Choice(2, "crumble"), Choice(3, "custard")))
    * ...     _ <- choicesTable.given(Condition('description -> "cake") or 'description -> "death").put(Choice(1, "victoria sponge"))
    * ...     _ <- choicesTable.given(Condition('description -> "cake") or 'description -> "death").put(Choice(2, "victoria sponge"))
    * ...     choices <- choicesTable.scan()
    * ...   } yield choices
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Choice(2,crumble)), Right(Choice(1,victoria sponge)), Right(Choice(3,custard)))
    * }}}
    *
    * The same forms of condition can be applied to deletions
    *
    * {{{
    * >>> case class Gremlin(number: Int, wet: Boolean, friendly: Boolean)
    * >>> val gremlinsTable = Table[Gremlin]("gremlins")
    * >>> LocalDynamoDB.withTable(client)("gremlins")('number -> N) {
    * ...   val ops = for {
    * ...     _ <- gremlinsTable.putAll(Set(Gremlin(1, false, true), Gremlin(2, true, false)))
    * ...     _ <- gremlinsTable.given('wet -> true).delete('number -> 1)
    * ...     _ <- gremlinsTable.given('wet -> true).delete('number -> 2)
    * ...     remainingGremlins <- gremlinsTable.scan()
    * ...   } yield remainingGremlins
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Gremlin(1,false,true)))
    * }}}
    *
    * and updates
    *
    * {{{
    * >>> LocalDynamoDB.withTable(client)("gremlins")('number -> N) {
    * ...   val ops = for {
    * ...     _ <- gremlinsTable.putAll(Set(Gremlin(1, false, true), Gremlin(2, true, true)))
    * ...     _ <- gremlinsTable.given('wet -> true).update('number -> 1, set('friendly -> false))
    * ...     _ <- gremlinsTable.given('wet -> true).update('number -> 2, set('friendly -> false))
    * ...     remainingGremlins <- gremlinsTable.scan()
    * ...   } yield remainingGremlins
    * ...   Scanamo.exec(client)(ops).toList
    * ... }
    * List(Right(Gremlin(2,true,false)), Right(Gremlin(1,false,true)))
    * }}}
    */
  def given[T: ConditionExpression](condition: T) = ConditionalOperation[V,T](name, condition)

  /**
    * Scans all elements of a table
    *
    * {{{
    * >>> case class Bear(name: String, favouriteFood: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> val table = Table[Bear]("bears")
    *
    * >>> LocalDynamoDB.withTable(client)("bears")('name -> S) {
    * ...   val ops = for {
    * ...     _ <- table.put(Bear("Pooh", "honey"))
    * ...     _ <- table.put(Bear("Yogi", "picnic baskets"))
    * ...     bears <- table.scan()
    * ...   } yield bears
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * List(Right(Bear(Pooh,honey)), Right(Bear(Yogi,picnic baskets)))
    * }}}
    */
  def scan() = Scannable.tableScannable[V].scan(this)

  /**
    * Query a table based on the hash key and optionally the range key
    *
    * {{{
    * >>> case class Transport(mode: String, line: String)
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> val table = Table[Transport]("transport")
    *
    * >>> import com.gu.scanamo.syntax._
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    *
    * >>> LocalDynamoDB.withTable(client)("transport")('mode -> S, 'line -> S) {
    * ...   val ops = for {
    * ...     _ <- table.putAll(Set(
    * ...       Transport("Underground", "Circle"),
    * ...       Transport("Underground", "Metropolitan"),
    * ...       Transport("Underground", "Central")
    * ...     ))
    * ...     linesBeginningWithC <- table.query('mode -> "Underground" and ('line beginsWith "C"))
    * ...   } yield linesBeginningWithC
    * ...   Scanamo.exec(client)(ops)
    * ... }
    * List(Right(Transport(Underground,Central)), Right(Transport(Underground,Circle)))
    * }}}
    */
  def query(query: Query[_], queryRequest: QueryRequest = new QueryRequest()) = Queryable.tableQueryable[V].query(this)(query: Query[_], queryRequest)
}

private[scanamo] case class Index[V: DynamoFormat](tableName: String, indexName: String) {
  /**
    * Query or scan an index, limiting the number of items evaluated by Dynamo
    * {{{
    * >>> case class Transport(mode: String, line: String, colour: String)
    * >>> val transport = Table[Transport]("transport")
    *
    * >>> val client = LocalDynamoDB.client()
    * >>> import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
    * >>> import com.gu.scanamo.syntax._
    *
    * >>> LocalDynamoDB.withTableWithSecondaryIndex(client)("transport", "colour-index")(
    * ...   'mode -> S, 'line -> S)('mode -> S, 'colour -> S
    * ... ) {
    * ...   val operations = for {
    * ...     _ <- transport.putAll(Set(
    * ...       Transport("Underground", "Circle", "Yellow"),
    * ...       Transport("Underground", "Metropolitan", "Magenta"),
    * ...       Transport("Underground", "Central", "Red"),
    * ...       Transport("Underground", "Picadilly", "Blue"),
    * ...       Transport("Underground", "Northern", "Black")))
    * ...     somethingBeginningWithBl <- transport.index("colour-index").limit(1).query(
    * ...       ('mode -> "Underground" and ('colour beginsWith "Bl")).descending
    * ...     )
    * ...   } yield somethingBeginningWithBl.toList
    * ...   Scanamo.exec(client)(operations)
    * ... }
    * List(Right(Transport(Underground,Picadilly,Blue)))
    * }}}
    */
  def limit(n: Int) = IndexLimit(this, n)

  def scan() = Scannable.indexScannable[V].scan(this)
  def query(query: Query[_], queryRequest: QueryRequest = new QueryRequest()) = Queryable.indexQueryable[V].query(this)(query: Query[_], queryRequest)
}

private[scanamo] case class TableLimit[V: DynamoFormat](table: Table[V], limit: Int) {
  def scan() = Scannable.limitedTableScannable[V].scan(this)
  def query(query: Query[_], queryRequest: QueryRequest = new QueryRequest()) = Queryable.limitedTableQueryable[V].query(this)(query: Query[_], queryRequest)
}
private[scanamo] case class IndexLimit[V: DynamoFormat](index: Index[V], limit: Int) {
  def scan() = Scannable.limitedIndexScannable[V].scan(this)
  def query(query: Query[_], queryRequest: QueryRequest = new QueryRequest()) = Queryable.limitedIndexQueryable[V].query(this)(query: Query[_], queryRequest)
}
private[scanamo] case class ConsistentlyReadTable[V: DynamoFormat](table: Table[V]) {
  def get(key: UniqueKey[_]) = Gettable.consistentGettable[V].get(this)(key: UniqueKey[_])
  def scan() = Scannable.consistentTableScannable[V].scan(this)
  def query(query: Query[_], queryRequest: QueryRequest = new QueryRequest()) = Queryable.consistentTableQueryable[V].query(this)(query: Query[_], queryRequest)
}

/* typeclass */trait Scannable[T[_], V] {
  def scan(t: T[V])(): ScanamoOps[List[Either[DynamoReadError, V]]]
}

object Scannable {
  def apply[T[_], V](implicit s: Scannable[T, V]) = s

  trait Ops[T[_], V] {
    val instance: Scannable[T, V]
    def self: T[V]
    def scan() = instance.scan(self)()
  }

  trait ToScannableOps {
    implicit def scannableOps[T[_], V](t: T[V])(implicit s: Scannable[T, V]) = new Ops[T, V] {
      val instance = s
      val self = t
    }
  }

  implicit def tableScannable[V: DynamoFormat] = new Scannable[Table, V] {
    override def scan(t: Table[V])(): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.scan[V](t.name)
  }
  implicit def indexScannable[V: DynamoFormat] = new Scannable[Index, V] {
    override def scan(i: Index[V])(): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.scanIndex[V](i.tableName, i.indexName)
  }

  implicit def limitedTableScannable[V: DynamoFormat] = new Scannable[TableLimit, V] {
    override def scan(t: TableLimit[V])(): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.scanWithLimit[V](t.table.name, t.limit)
  }
  implicit def limitedIndexScannable[V: DynamoFormat] = new Scannable[IndexLimit, V] {
    override def scan(i: IndexLimit[V])(): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.scanIndexWithLimit[V](i.index.tableName, i.index.indexName, i.limit)
  }

  implicit def consistentTableScannable[V: DynamoFormat]() = new Scannable[ConsistentlyReadTable, V] {
    override def scan(t: ConsistentlyReadTable[V])(): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.scanConsistent[V](t.table.name)
  }
}

/* typeclass */ trait Queryable[T[_], V] {
  def query(t: T[V])(query: Query[_], queryRequest: QueryRequest = new QueryRequest()): ScanamoOps[List[Either[DynamoReadError, V]]]
}

object Queryable {
  def apply[T[_], V](implicit s: Queryable[T, V]) = s

  trait Ops[T[_], V] {
    val instance: Queryable[T, V]
    def self: T[V]
    def query(query: Query[_], queryRequest: QueryRequest) = instance.query(self)(query, queryRequest)
  }

  trait ToQueryableOps {
    implicit def queryableOps[T[_], V](t: T[V])(implicit s: Queryable[T, V]) = new Ops[T, V] {
      val instance = s
      val self = t
    }
  }

  implicit def tableQueryable[V: DynamoFormat] = new Queryable[Table, V] {
    override def query(t: Table[V])(query: Query[_], queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.query[V](t.name)(query, queryRequest)
  }
  implicit def indexQueryable[V: DynamoFormat] = new Queryable[Index, V] {
    override def query(i: Index[V])(query: Query[_], queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.queryIndex[V](i.tableName, i.indexName)(query, queryRequest)
  }
  implicit def limitedTableQueryable[V: DynamoFormat] = new Queryable[TableLimit, V] {
    override def query(t: TableLimit[V])(query: Query[_], queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.queryWithLimit[V](t.table.name)(query, t.limit, queryRequest)
  }
  implicit def limitedIndexQueryable[V: DynamoFormat] = new Queryable[IndexLimit, V] {
    override def query(i: IndexLimit[V])(query: Query[_], queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.queryIndexWithLimit[V](i.index.tableName, i.index.indexName)(query, i.limit, queryRequest)
  }
  implicit def consistentTableQueryable[V: DynamoFormat] = new Queryable[ConsistentlyReadTable, V] {
    override def query(t: ConsistentlyReadTable[V])(query: Query[_], queryRequest: QueryRequest): ScanamoOps[List[Either[DynamoReadError, V]]] =
      ScanamoFree.queryConsistent[V](t.table.name)(query, queryRequest)
  }
}

/* typeclass */ trait Gettable[T[_], V]{
  def get(t: T[V])(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]]
}

object Gettable {
  def apply[T[_], V](implicit s: Gettable[T, V]) = s

  implicit def consistentGettable[V: DynamoFormat] = new Gettable[ConsistentlyReadTable, V] {
    override def get(t: ConsistentlyReadTable[V])(key: UniqueKey[_]): ScanamoOps[Option[Either[DynamoReadError, V]]] =
      ScanamoFree.getWithConsistency[V](t.table.name)(key)
  }
}
