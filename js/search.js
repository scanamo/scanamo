// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Batch Operations",
      "url": "/batch-operations.html",
      "content": "Batch Operations Many operations against Dynamo can be performed in batches. Scanamo has support for putting, getting and deleting in batches import org.scanamo._ import org.scanamo.syntax._ import org.scanamo.generic.auto._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ val client = LocalDynamoDB.syncClient() val scanamo = Scanamo(client) LocalDynamoDB.createTable(client)(\"lemmings\")(\"role\" -&gt; S) case class Lemming(role: String, number: Long) val lemmingsTable = Table[Lemming](\"lemmings\") // lemmingsTable: Table[Lemming] = Table(\"lemmings\") val ops = for { _ &lt;- lemmingsTable.putAll(Set( Lemming(\"Walker\", 99), Lemming(\"Blocker\", 42), Lemming(\"Builder\", 180) )) bLemmings &lt;- lemmingsTable.getAll(\"role\" in Set(\"Blocker\", \"Builder\")) _ &lt;- lemmingsTable.deleteAll(\"role\" in Set(\"Walker\", \"Blocker\")) survivors &lt;- lemmingsTable.scan() } yield (bLemmings, survivors) // ops: cats.free.Free[org.scanamo.ops.ScanamoOpsA, (Set[Either[DynamoReadError, Lemming]], List[Either[DynamoReadError, Lemming]])] = FlatMapped( // FlatMapped( // Suspend( // BatchWrite( // BatchWriteItemRequest(RequestItems={lemmings=[WriteRequest(PutRequest=PutRequest(Item={number=AttributeValue(N=99), role=AttributeValue(S=Walker)})), WriteRequest(PutRequest=PutRequest(Item={number=AttributeValue(N=42), role=AttributeValue(S=Blocker)})), WriteRequest(PutRequest=PutRequest(Item={number=AttributeValue(N=180), role=AttributeValue(S=Builder)}))]}) // ) // ), // org.scanamo.ScanamoFree$$$Lambda$10894/673184086@1a1ec96c // ), // &lt;function1&gt; // ) val (bLemmings, survivors) = scanamo.exec(ops) // bLemmings: Set[Either[DynamoReadError, Lemming]] = Set( // Right(Lemming(\"Builder\", 180L)), // Right(Lemming(\"Blocker\", 42L)) // ) // survivors: List[Either[DynamoReadError, Lemming]] = List( // Right(Lemming(\"Builder\", 180L)) // ) bLemmings.flatMap(_.toOption) // res1: Set[Lemming] = Set(Lemming(\"Builder\", 180L), Lemming(\"Blocker\", 42L)) survivors.flatMap(_.toOption) // res2: List[Lemming] = List(Lemming(\"Builder\", 180L))"
    } ,    
    {
      "title": "Conditional Operations",
      "url": "/conditional-operations.html",
      "content": "Conditional Operations Modifying operations (Put, Delete, Update) can be performed conditionally, so that they only have an effect if some state of the DynamoDB table is true at the time of execution. import org.scanamo._ import org.scanamo.syntax._ import org.scanamo.generic.auto._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ val client = LocalDynamoDB.syncClient() val scanamo = Scanamo(client) case class Gremlin(number: Int, name: String, wet: Boolean, friendly: Boolean) val gremlinsTable = Table[Gremlin](\"gremlins\") // gremlinsTable: Table[Gremlin] = Table(\"gremlins\") LocalDynamoDB.withTable(client)(\"gremlins\")(\"number\" -&gt; N) { val ops = for { _ &lt;- gremlinsTable.putAll( Set(Gremlin(1, \"Gizmo\", false, true), Gremlin(2, \"George\", true, false))) // Only `put` Gremlins if not already one with the same number _ &lt;- gremlinsTable.when(not(attributeExists(\"number\"))) .put(Gremlin(2, \"Stripe\", false, true)) _ &lt;- gremlinsTable.when(not(attributeExists(\"number\"))) .put(Gremlin(3, \"Greta\", true, true)) allGremlins &lt;- gremlinsTable.scan() _ &lt;- gremlinsTable.when(\"wet\" === true) .delete(\"number\" === 1) _ &lt;- gremlinsTable.when(\"wet\" === true) .delete(\"number\" === 2) _ &lt;- gremlinsTable.when(\"wet\" === true) .update(\"number\" === 1, set(\"friendly\", false)) _ &lt;- gremlinsTable.when(\"wet\" === true) .update(\"number\" === 3, set(\"friendly\", false)) remainingGremlins &lt;- gremlinsTable.scan() } yield (allGremlins, remainingGremlins) scanamo.exec(ops) } // res0: (List[Either[DynamoReadError, Gremlin]], List[Either[DynamoReadError, Gremlin]]) = ( // List( // Right(Gremlin(2, \"George\", true, false)), // Right(Gremlin(1, \"Gizmo\", false, true)), // Right(Gremlin(3, \"Greta\", true, true)) // ), // List( // Right(Gremlin(1, \"Gizmo\", false, true)), // Right(Gremlin(3, \"Greta\", true, false)) // ) // ) More examples can be found in the Table scaladoc."
    } ,    
    {
      "title": "DynamoFormat",
      "url": "/dynamo-format.html",
      "content": "DynamoFormat Scanamo uses the DynamoFormat type class to define how to read and write different types to DynamoDB. Many common types have a DynamoFormat provided by Scanamo. For a full list see of those supported, you can look at the companion object. Scanamo also supports automatically deriving formats for case classes and sealed trait families where all the contained types have a defined or derivable DynamoFormat. Automatic Derivation Scanamo can automatically derive DynamoFormat for case classes (as long as all its members can also be derived). Ex: import org.scanamo._ import org.scanamo.generic.auto._ case class Farm(animals: List[String]) case class Farmer(name: String, age: Long, farm: Farm) val table = Table[Farmer](\"farmer\") table.putAll( Set( Farmer(\"McDonald\", 156L, Farm(List(\"sheep\", \"cow\"))), Farmer(\"Boggis\", 43L, Farm(List(\"chicken\"))) ) ) Semi-automatic Derivation Scanamo offers a convenient way (semi-automatic) to derive DynamoFormat in your code. Ex: import org.scanamo._ import org.scanamo.generic.semiauto._ case class Farm(animals: List[String]) case class Farmer(name: String, age: Long, farm: Farm) implicit val formatFarm: DynamoFormat[Farm] = deriveDynamoFormat implicit val formatFarmer: DynamoFormat[Farmer] = deriveDynamoFormat Custom Formats It’s also possible to define a serialisation format for types which Scanamo doesn’t already support and can’t derive. Normally this involves using the DynamoFormat.xmap or DynamoFormat.coercedXmap to translate between the type and one Scanamo does already know about. For example, to store Joda DateTime objects as ISO Strings in Dynamo: import org.joda.time._ import org.scanamo._ import org.scanamo.generic.auto._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ case class Foo(dateTime: DateTime) val client = LocalDynamoDB.syncClient() // client: software.amazon.awssdk.services.dynamodb.DynamoDbClient = software.amazon.awssdk.services.dynamodb.DefaultDynamoDbClient@50dc1d1b val scanamo = Scanamo(client) // scanamo: Scanamo = org.scanamo.Scanamo@219ff676 LocalDynamoDB.createTable(client)(\"foo\")(\"dateTime\" -&gt; S) // res3: software.amazon.awssdk.services.dynamodb.model.CreateTableResponse = CreateTableResponse(TableDescription=TableDescription(AttributeDefinitions=[AttributeDefinition(AttributeName=dateTime, AttributeType=S)], TableName=foo, KeySchema=[KeySchemaElement(AttributeName=dateTime, KeyType=HASH)], TableStatus=ACTIVE, CreationDateTime=2021-03-02T01:09:07.401Z, ProvisionedThroughput=ProvisionedThroughputDescription(LastIncreaseDateTime=1970-01-01T00:00:00Z, LastDecreaseDateTime=1970-01-01T00:00:00Z, NumberOfDecreasesToday=0, ReadCapacityUnits=1, WriteCapacityUnits=1), TableSizeBytes=0, ItemCount=0, TableArn=arn:aws:dynamodb:ddblocal:000000000000:table/foo)) implicit val jodaStringFormat = DynamoFormat.coercedXmap[DateTime, String, IllegalArgumentException]( DateTime.parse(_).withZone(DateTimeZone.UTC), _.toString ) // jodaStringFormat: DynamoFormat[DateTime] = org.scanamo.DynamoFormat$$anon$4@71143b3d val fooTable = Table[Foo](\"foo\") // fooTable: Table[Foo] = Table(\"foo\") scanamo.exec { for { _ &lt;- fooTable.put(Foo(new DateTime(0))) results &lt;- fooTable.scan() } yield results }.toList // res4: List[Either[DynamoReadError, Foo]] = List( // Right(Foo(1970-01-01T00:00:00.000Z)) // ) Formats for Refined Types Scanamo supports Scala refined types via the scanamo-refined module, helping you to define custom formats for types built using the predicates provided by the refined project. Refined types give an extra layer of type safety to our programs making the compilation fail when we try to assign wrong values to them. To use them in your project you will need to include the dependency in your project: libraryDependencies += \"org.scanamo\" %% \"scanamo-refined\" % \"x.y.z\" And then import the support for refined types and define your model: import org.scanamo._ import eu.timepit.refined._ import eu.timepit.refined.api.Refined import eu.timepit.refined.auto._ import eu.timepit.refined.numeric._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ val client = LocalDynamoDB.syncClient() val scanamo = Scanamo(client) type PosInt = Int Refined Positive case class Customer(age: PosInt) LocalDynamoDB.createTable(client)(\"Customer\")(\"age\" -&gt; N) You just now use it like if the type PosInt was natively supported by scanamo: import org.scanamo.refined._ import org.scanamo.generic.auto._ val customerTable = Table[Customer](\"Customer\") // customerTable: Table[Customer] = Table(\"Customer\") scanamo.exec { for { _ &lt;- customerTable.put(Customer(67)) results &lt;- customerTable.scan() } yield results }.toList // res7: List[Either[DynamoReadError, Customer]] = List(Right(Customer(67))) Derived Formats Scanamo uses magnolia and implicit derivation to automatically derive DynamoFormats for case classes and sealed trait families. You may also see or hear sealed trait families referred to as Algebraic Data Types (ADTs) and co-products. Here is an example that could be used to support event sourcing (assuming a table with a partition key of id and sort key seqNo): import java.util.UUID import org.scanamo._ // Sealed trait family for events. sealed trait Event case class Create(name: String) extends Event case class Delete(reason: String) extends Event // An event envelope that wraps events. case class EventEnvelope(id: UUID, seqNo: Int, event: Event) // Example instantiations. val id = UUID.fromString(\"9e5fd6e9-65ef-472c-ad89-e5fe658f14c6\") val create = EventEnvelope(id, 0, Create(\"Something\")) val delete = EventEnvelope(id, 1, Delete(\"Oops\")) import org.scanamo._ import org.scanamo.generic.auto._ val attributeValue = DynamoFormat[EventEnvelope].write(create) // attributeValue: DynamoValue = DynObject( // Pure( // Map( // \"event\" -&gt; DynObject( // Pure( // Map( // \"Create\" -&gt; DynObject(Pure(Map(\"name\" -&gt; DynString(\"Something\")))) // ) // ) // ), // \"seqNo\" -&gt; DynNum(\"0\"), // \"id\" -&gt; DynString(\"9e5fd6e9-65ef-472c-ad89-e5fe658f14c6\") // ) // ) // ) val dynamoRecord = DynamoFormat[EventEnvelope].read(attributeValue) // dynamoRecord: Either[DynamoReadError, EventEnvelope] = Right( // EventEnvelope(9e5fd6e9-65ef-472c-ad89-e5fe658f14c6, 0, Create(\"Something\")) // ) If you look carefully at the attribute value (pretty-printed below) then you can see that the envelope (or wrapper) is necessary. This is because Scanamo writes the sealed trait family and associated case classes into a map. This allows Scanamo to use the map key to disambiguate between the sub-classes when reading attribute values. This makes sense and works well for most use cases, but it does mean you cannot persist an sealed trait family directly (i.e. without an envelope or wrapper) using automatic derivation because partition keys do not support maps. Here is the pretty-printed attribute value that Scanamo generates: { \"M\": { \"seqNo\": { \"N\": 0 }, \"id\": { \"S\": \"9e5fd6e9-65ef-472c-ad89-e5fe658f14c6\" }, \"event\": { \"M\": { \"Create\": { \"M\": { \"name\":{ \"S\": \"Something\" } } } } } } }"
    } ,    
    {
      "title": "Filters",
      "url": "/filters.html",
      "content": "Filters Scans and Queries can be filtered within Dynamo, preventing the memory, network and marshalling overhead of filtering on the client. Note that these filters do not reduce the consumed capacity in Dynamo. Even though a filter may lead to a small number of results being returned, it could still exhaust the provisioned capacity or force the provisioned capacity to autoscale up to an expensive level. import org.scanamo._ import org.scanamo.syntax._ import org.scanamo.generic.auto._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ val client = LocalDynamoDB.syncClient() val scanamo = Scanamo(client) case class Station(line: String, name: String, zone: Int) val stationTable = Table[Station](\"Station\") LocalDynamoDB.withTable(client)(\"Station\")(\"line\" -&gt; S, \"name\" -&gt; S) { val ops = for { _ &lt;- stationTable.putAll(Set( Station(\"Metropolitan\", \"Chalfont &amp; Latimer\", 8), Station(\"Metropolitan\", \"Chorleywood\", 7), Station(\"Metropolitan\", \"Rickmansworth\", 7), Station(\"Metropolitan\", \"Croxley\", 7), Station(\"Jubilee\", \"Canons Park\", 5) )) filteredStations &lt;- stationTable .filter(\"zone\" &lt; 8) .query(\"line\" === \"Metropolitan\" and (\"name\" beginsWith \"C\")) } yield filteredStations scanamo.exec(ops) } // res0: List[Either[DynamoReadError, Station]] = List( // Right(Station(\"Metropolitan\", \"Chorleywood\", 7)), // Right(Station(\"Metropolitan\", \"Croxley\", 7)) // ) More examples can be found in the Table scaladoc."
    } ,        
    {
      "title": "Operations",
      "url": "/operations.html",
      "content": "Operations Scanamo supports all the DynamoDB operations that interact with individual items in DynamoDB tables: Put for adding a new item, or replacing an existing one Get for retrieving an item by a fully specified key Delete for removing an item Update for updating some portion of the fields of an item, whilst leaving the rest as is Scan for retrieving all elements of a table Query for retrieving all elements with a given hash-key and a range key that matches some criteria Scanamo also supports batched operations, conditional operations and queries against secondary indexes. Put and Get Often when using DynamoDB, the primary use case is simply putting objects into Dynamo and subsequently retrieving them: import org.scanamo._ import org.scanamo.syntax._ import org.scanamo.generic.auto._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ val client = LocalDynamoDB.syncClient() val scanamo = Scanamo(client) LocalDynamoDB.createTable(client)(\"muppets\")(\"name\" -&gt; S) case class Muppet(name: String, species: String) val muppets = Table[Muppet](\"muppets\") // muppets: Table[Muppet] = Table(\"muppets\") scanamo.exec { for { _ &lt;- muppets.put(Muppet(\"Kermit\", \"Frog\")) _ &lt;- muppets.put(Muppet(\"Cookie Monster\", \"Monster\")) _ &lt;- muppets.put(Muppet(\"Miss Piggy\", \"Pig\")) kermit &lt;- muppets.get(\"name\" === \"Kermit\") } yield kermit } // res1: Option[Either[DynamoReadError, Muppet]] = Some( // Right(Muppet(\"Kermit\", \"Frog\")) // ) Note that when using Table no operations are actually executed against DynamoDB until exec is called. Delete To remove an item in its entirety, we can use delete: import org.scanamo._ import org.scanamo.syntax._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ LocalDynamoDB.createTable(client)(\"villains\")(\"name\" -&gt; S) case class Villain(name: String, catchphrase: String) val villains = Table[Villain](\"villains\") // villains: Table[Villain] = Table(\"villains\") scanamo.exec { for { _ &lt;- villains.put(Villain(\"Dalek\", \"EXTERMINATE!\")) _ &lt;- villains.put(Villain(\"Cyberman\", \"DELETE\")) _ &lt;- villains.delete(\"name\" === \"Cyberman\") survivors &lt;- villains.scan() } yield survivors } // res3: List[Either[DynamoReadError, Villain]] = List( // Right(Villain(\"Dalek\", \"EXTERMINATE!\")) // ) Update If you want to change some of the fields of an item, that don’t form part of its key, without replacing the item entirely, you can use the update operation: import org.scanamo._ import org.scanamo.syntax._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ LocalDynamoDB.createTable(client)(\"teams\")(\"name\" -&gt; S) case class Team(name: String, goals: Int, scorers: List[String], mascot: Option[String]) val teamTable = Table[Team](\"teams\") // teamTable: Table[Team] = Table(\"teams\") scanamo.exec { for { _ &lt;- teamTable.put(Team(\"Watford\", 1, List(\"Blissett\"), Some(\"Harry the Hornet\"))) updated &lt;- teamTable.update(\"name\" === \"Watford\", set(\"goals\", 2) and append(\"scorers\", \"Barnes\") and remove(\"mascot\")) } yield updated } // res5: Either[DynamoReadError, Team] = Right( // Team(\"Watford\", 2, List(\"Blissett\", \"Barnes\"), None) // ) Which fields are updated can be based on incoming data: import cats.data.NonEmptyList import org.scanamo.ops.ScanamoOps import org.scanamo.DynamoReadError import org.scanamo.update.UpdateExpression LocalDynamoDB.createTable(client)(\"favourites\")(\"name\" -&gt; S) case class Favourites(name: String, colour: String, number: Long) val favouritesTable = Table[Favourites](\"favourites\") // favouritesTable: Table[Favourites] = Table(\"favourites\") scanamo.exec(favouritesTable.put(Favourites(\"Alice\", \"Blue\", 42L))) case class FavouriteUpdate(name: String, colour: Option[String], number: Option[Long]) def updateFavourite(fu: FavouriteUpdate): Option[ScanamoOps[Either[DynamoReadError, Favourites]]] = { val updates: List[UpdateExpression] = List( fu.colour.map(c =&gt; set(\"colour\", c)), fu.number.map(n =&gt; set(\"number\", n)) ).flatten NonEmptyList.fromList(updates).map(ups =&gt; favouritesTable.update(\"name\" === fu.name, ups.reduce[UpdateExpression](_ and _)) ) } import cats.implicits._ val updates = List( FavouriteUpdate(\"Alice\", Some(\"Aquamarine\"), Some(93L)), FavouriteUpdate(\"Alice\", Some(\"Red\"), None), FavouriteUpdate(\"Alice\", None, None) ) // updates: List[FavouriteUpdate] = List( // FavouriteUpdate(\"Alice\", Some(\"Aquamarine\"), Some(93L)), // FavouriteUpdate(\"Alice\", Some(\"Red\"), None), // FavouriteUpdate(\"Alice\", None, None) // ) scanamo.exec( for { _ &lt;- updates.flatMap(updateFavourite).sequence result &lt;- favouritesTable.get(\"name\" === \"Alice\") } yield result ) // res8: Option[Either[DynamoReadError, Favourites]] = Some( // Right(Favourites(\"Alice\", \"Red\", 93L)) // ) Further examples, showcasing different types of update can be found in the scaladoc for the update method on Table. Scan If you want to go through all elements of a table, or index, Scanamo supports scanning it: import org.scanamo._ import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ LocalDynamoDB.createTable(client)(\"lines\")(\"mode\" -&gt; S, \"line\" -&gt; S) case class Transport(mode: String, line: String) val transportTable = Table[Transport](\"lines\") // transportTable: Table[Transport] = Table(\"lines\") val operations = for { _ &lt;- transportTable.putAll(Set( Transport(\"Underground\", \"Circle\"), Transport(\"Underground\", \"Metropolitan\"), Transport(\"Underground\", \"Central\"), Transport(\"Tram\", \"Croydon Tramlink\") )) allLines &lt;- transportTable.scan() } yield allLines.toList // operations: cats.free.Free[ops.ScanamoOpsA, List[Either[DynamoReadError, Transport]]] = FlatMapped( // FlatMapped( // Suspend( // BatchWrite( // BatchWriteItemRequest(RequestItems={lines=[WriteRequest(PutRequest=PutRequest(Item={mode=AttributeValue(S=Underground), line=AttributeValue(S=Circle)})), WriteRequest(PutRequest=PutRequest(Item={mode=AttributeValue(S=Underground), line=AttributeValue(S=Metropolitan)})), WriteRequest(PutRequest=PutRequest(Item={mode=AttributeValue(S=Underground), line=AttributeValue(S=Central)})), WriteRequest(PutRequest=PutRequest(Item={mode=AttributeValue(S=Tram), line=AttributeValue(S=Croydon Tramlink)}))]}) // ) // ), // org.scanamo.ScanamoFree$$$Lambda$10894/673184086@353a73a5 // ), // &lt;function1&gt; // ) scanamo.exec(operations) // res10: List[Either[DynamoReadError, Transport]] = List( // Right(Transport(\"Tram\", \"Croydon Tramlink\")), // Right(Transport(\"Underground\", \"Central\")), // Right(Transport(\"Underground\", \"Circle\")), // Right(Transport(\"Underground\", \"Metropolitan\")) // ) Query Scanamo can be used to perform most queries that can be made against DynamoDB scanamo.exec { for { _ &lt;- transportTable.putAll(Set( Transport(\"Underground\", \"Circle\"), Transport(\"Underground\", \"Metropolitan\"), Transport(\"Underground\", \"Central\") )) tubesStartingWithC &lt;- transportTable.query(\"mode\" === \"Underground\" and (\"line\" beginsWith \"C\")) } yield tubesStartingWithC.toList } // res11: List[Either[DynamoReadError, Transport]] = List( // Right(Transport(\"Underground\", \"Central\")), // Right(Transport(\"Underground\", \"Circle\")) // )"
    } ,      
    {
      "title": "Using Indexes",
      "url": "/using-indexes.html",
      "content": "Using Indexes Scanamo supports scanning and querying global secondary indexes. In the following example, we create and use a table called transport with a hash key of mode and range key of line and a global secondary called colour-index with only a hash key on the colour attribute: import org.scanamo._ import org.scanamo.syntax._ import org.scanamo.generic.auto._ case class Transport(mode: String, line: String, colour: String) val transport = Table[Transport](\"transport\") val colourIndex = transport.index(\"colour-index\") val client = LocalDynamoDB.syncClient() val scanamo = Scanamo(client) import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._ LocalDynamoDB.withTableWithSecondaryIndex(client)(\"transport\", \"colour-index\")(\"mode\" -&gt; S, \"line\" -&gt; S)(\"colour\" -&gt; S) { val operations = for { _ &lt;- transport.putAll(Set( Transport(\"Underground\", \"Circle\", \"Yellow\"), Transport(\"Underground\", \"Metropolitan\", \"Maroon\"), Transport(\"Underground\", \"Central\", \"Red\"))) maroonLine &lt;- colourIndex.query(\"colour\" === \"Maroon\") } yield maroonLine.toList scanamo.exec(operations) } // res0: List[Either[DynamoReadError, Transport]] = List( // Right(Transport(\"Underground\", \"Metropolitan\", \"Maroon\")) // )"
    }    
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
