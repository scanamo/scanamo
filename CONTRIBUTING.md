## Issues & Questions

If you've found what looks like a bug, please check [issues on GitHub](https://github.com/scanamo/scanamo/issues),
and create a new issue if necessary.

If you just have a general question, or there's something you don't understand, ask on
[stackoverflow.com](http://stackoverflow.com/questions/ask) (tag it with [`scanamo`](http://stackoverflow.com/questions/tagged/scanamo)
so we can see it) - there are many more people who can answer that sort of question on Stackoverflow,
you stand a good chance of getting your question answered quicker!

There _was_ a [Gitter channel](https://gitter.im/guardian/scanamo) for discussion/questions around Scanamo, but
it's not a preferred channel for the current maintenance team, and isn't checked very often.

## Pull requests

### Documentation

Suggestions on how to improve the documentation are very welcome!

### New features

New features inevitably grow the Scanamo codebase, unfortunately increasing its ongoing
maintenance burden. As maintenance time is precious and in short supply, new features need
to justify themselves in terms of benefit-to-users versus cost-in-maintenance - maintenance
cost can be roughly correlated to how much implementation code is added, as an example, see
[PR #1801](https://github.com/scanamo/scanamo/pull/1801#pullrequestreview-2258441456).

New features that don't significantly improve on just-directly-using-the-AWS-SDK stand an increased
risk of being rejected. Of the ~60 operations supported by the [DynamoDB API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Operations_Amazon_DynamoDB.html),
only 9 are directly supported by Scanamo - for each supported operation, the benefits are
usually _more_ than one from this list:

* **conciseness** - accomplishing tasks with less code than directly using the AWS SDK
* **preventing the construction of invalid requests** (eg, `expressionAttributeNames` & `expressionAttributeValues` correctly set, avoiding reserved words, etc)
* **facilitating mapping** - between Scala/Java data types/classes and the DynamoDB item attributes model

Scanamo's maintainers have little opportunity to see how others use Scanamo - it may
help maintainers understand benefit if you can describe a specific example of how you intend to use
the feature (the best example would be linking to a draft public PR that uses the feature!).

## Building and testing Scanamo

Scanamo uses a standard [SBT](https://www.scala-sbt.org/) build. If you
have SBT installed, you should first run `startDynamodbLocal` task from the SBT prompt to start a local dynamodb instance and afterwards run the `test` command to compile Scanamo and run its tests.

Scanamo currently uses [`scalafmt`](https://scalameta.org/scalafmt/) to
standardise code-style - the CI build will fail if formatting doesn't
match the defined rules. You can get Scalafmt to reformat your code like
this at the `sbt` prompt:

```
scalafmt
scalafmtSbt
```

...and you can check the changes are good with:

```
scalafmtCheck
scalafmtSbtCheck
```

## Publishing a new release

This repo uses [`gha-scala-library-release-workflow`](https://github.com/guardian/gha-scala-library-release-workflow)
to automate publishing releases (both full & preview releases) - see
[**Making a Release**](https://github.com/guardian/gha-scala-library-release-workflow/blob/main/docs/making-a-release.md).
