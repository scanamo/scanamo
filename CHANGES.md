## Version 0.6.0

New feature:

 * support for `update` operations
 * cross-published for Scala 2.10.x

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