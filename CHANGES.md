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
