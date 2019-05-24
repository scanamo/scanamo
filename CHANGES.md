## Version 1.0.0-M10

- https://github.com/scanamo/scanamo/pull/295 Add missing floatFormat and floatSetFormat to DynamoFormat (@shtukas)
- https://github.com/scanamo/scanamo/pull/365 Return a Left of MissingKeyValue if the key provided is not the primary key (@susiecoleman)
- https://github.com/scanamo/scanamo/pull/367 Write empty strings as null (@regiskuckaertz)
- https://github.com/scanamo/scanamo/pull/377 Add startDynamodbLocal sbt task information to CONTRIBUTING.md (@kiranbayram)
- https://github.com/scanamo/scanamo/pull/381 Fix links in doc pages (@kiranbayram)
- https://github.com/scanamo/scanamo/pull/400 Manual derivation with `DynamoValue` (@regiskuckaertz)
- https://github.com/scanamo/scanamo/pull/402 Add retry mechanism for AlpakkaScanamo (@AyushTiwary)
- https://github.com/scanamo/scanamo/pull/405 Upgrade to alpakka v1.0.1 (@regiskuckaertz)
- https://github.com/scanamo/scanamo/pull/406 Breaking: Refactor Scanamo clients as classes (@regiskuckaertz) 

## Version 1.0.0-M9

In this release, new effect modules have been added for cats-effect, scalaz and scalaz-zio 🎉

* Added `scanamo-scalaz-zio`, an interpreter for the new Scalaz IO monad (https://github.com/scanamo/scanamo/pull/262)
* Added cleaner formatting rules, PRs will be checked for proper formatting from now on (https://github.com/scanamo/scanamo/pull/258)
* All functions but `exec` have been marked as deprecated in `Scanamo`, `ScanamoAsync`, `ScanamoCats`, `ScanamoAlpakka` and `ScanamoScalaz` (https://github.com/scanamo/scanamo/pull/257)
* Read a Set which has been deleted after an update operation (https://github.com/scanamo/scanamo/pull/250 - @Slakah)
* Upgrade to cats to 1.3.1 and cats-effect to 1.0.0 (https://github.com/scanamo/scanamo/pull/254/files)
* Upgrade to sbt 1.2.3 (https://github.com/scanamo/scanamo/pull/259)
* Upgrade to alpakka 0.20 (https://github.com/scanamo/scanamo/pull/252 - @Slakah) 
* Add pagination API (https://github.com/scanamo/scanamo/pull/227)
* `DynamoFormat` for Java's `Instant` and `OffsetDateTime` (https://github.com/scanamo/scanamo/pull/229) and for Joda's `DateTime` (https://github.com/scanamo/scanamo/pull/220)

## Version 1.0.0-M6

 * Aggregate `scanamo-refined` in the root project (#194)

## Version 1.0.0-M5

 * Add `getAllWithConsistency` (#184 - @Slakah)
 * Add support for refined types in `scanamo-refined` submodule (#182 - @alonsodomin)
 * Improve documentation (#193 and #190 - @howardjohn and @philwills)
 * Allow append/prepend of list values (#187 - @prurph)

## Version 1.0.0-M4

 * Update cats to 1.0.1
 * Update shapeless to 2.3.3
 * Update aws-java-sdk-dynamodb 1.11.256
 * Update scalatest to 3.0.4
 * Update scalacheck to 1.13.5
 * Update travis badge to reflect org move

## Version 1.0.0-M3

 * Update Cats to `1.0.0-RC1` (#166)
 * Equals condition support for nested attributes (#165 - @ivashin)
 * Update aws-java-sdk-dynamodb and alpakka (#167)

## Version 1.0.0-M2

 * Add support for [Alpakka](http://developer.lightbend.com/docs/alpakka/current/dynamodb.html) as a client (#151 - @btlines), plus docs (#158 - @calvinlfer)
 * Return overwritten item from `Put` (#153 - @amirkarimi)
 * BigDecimal support (#161 - @hunzinker)
 * Support conditions on nested attribute names (#156)
 * Rename `Index` to `SecondaryIndex` (#144)

## Version 1.0.0-M1

 * Update Cats to `1.0.0-MF` (#134 - @rstradling)
 * Don't allow an empty map to be derived as a DynamoFormat (#140)

## Version 0.9.5

 * Automatically manage batching of `getAll` (#116 - @todor-kolev)
 * Support filtering by attribute existence on scans (#119)
 * Allow querying on an exact hash and range key (#122)

## Version 0.9.4

 * Add "attribute_not_exists" condition expression (#110 - @todor-kolev)

## Version 0.9.3

 * Support for filtering queries and scans (#102) 
 * Added support for 'between' queries (#106 - @todor-kolev)
 * Allow one attribute to be updated to the value of another (#101)

## Version 0.9.2

 * Fix bug when many updates and'd together (#90)

## Version 0.9.1

 * Allow update of nested properties (#89)

## Version 0.9.0

 * Automatic derivation of `DynamoFormat` for sealed traits (#78 - @cb372)
 * Automatic derivation of `DynamoFormat` for enumerations (#84)
 * Allow Update operations to be constructed programmatically (#77)
 * Default `DynamoFormat` for arrays (#79 - @timchan-lumoslabs)
 * Default `DynamoFormat` for `UUID` (#81)
 
 * Remove compiler messages on failure of `DyanmoFormat` derivation (#85)
 
 * Various library upgrades including cats 0.9.0 (#86)
 
0.9.0 is largely source compatible with 0.8.x (no tests had to change structure),
but the encoding of `UpdateExpression` means it is no longer open for extension

## Version 0.8.3

 * Add support for consistent get/scan/query operations (#74 - @amherrington13)

## Version 0.8.2

 * Add support for deleteAll (#70 - @randallalexander)

## Version 0.8.1

 * Release for Scala 2.12 in addition to 2.11

## Version 0.8.0

Definitely Breaking changes:

 * Switched from using `Xor` to `Either` (#67)

Possibly breaking changes:

 * Return the new value after an update (#66)
 * Read Dynamo NULL values as `None` for values mapped to an `Option` (#65)

Innocent changes:

 * Attempt to provide better errors when unable derive a `DynamoFormat` (#64)
 * Add REMOVE support to the update API (#63 - @cb372)
 * Add an `iso` method to `DynamoFormat` (#62 - @cb372)


## Version 0.7.0

 * default `DynamoFormat` instances for `Byte` and `Array[Byte]` (@drocsid)
 * default `DynamoFormat` instance for `Seq` (@paulmr)
 * default `DynamoFormat` instance for `Short`
 * upgrade to Cats 0.7.0 (@travisbrown)
 * added `scan` and `query` methods explicitly to `Table` and `Index`

Breaking change:

 * Bulk operations(`putAll` and `getAll`) now take a `Set` rather than `List`, which
 better reflects the underlying behaviour

## Version 0.6.0

New feature:

 * support for `update` operations

## Version 0.5.0

New features:

 * support for conditional `put` and `delete` operations via the `given` method on `Table`
 * support for limiting the number of items evaluated by `query` and `scan` operations
 * a default `DynamoFormat` instance for `Vector`
 
Breaking changes:

 * `query` and `scan` operations now return a `List`, not a `Stream`, as they were being 
 eagerly evaluated


## Version 0.4.0

> 2016 April 29

New features:

 * adds `Table` to try and simplify common use cases [#21](https://github.com/guardian/scanamo/pull/21)
 * adds support for queries that return results in descending range key order [#23](https://github.com/guardian/scanamo/pull/23)
 * adds default `DynamoFormat` instances for `Double`, `Set[Int]`, `Set[Long]`, `Set[Double]` and `Set[String]` [#24](https://github.com/guardian/scanamo/pull/24)
 * adds `coercedXmap` to `DynamoFormat` for the common case of serialisation that should always work, 
 but deserialisation that is only valid for a subset of the serialised type [#14](https://github.com/guardian/scanamo/pull/14)
 
Breaking changes:
 
 * replaces `cats.data.Streaming` with `collection.immutable.Stream` [#13](https://github.com/guardian/scanamo/pull/13)
 * replaces `cats.data.ValidatedNel` with `cats.data.Xor` in public interface 
 [#15](https://github.com/guardian/scanamo/pull/15) and [#16](https://github.com/guardian/scanamo/pull/16)
 * moves the packages that a number of types live in [#17](https://github.com/guardian/scanamo/pull/17)
