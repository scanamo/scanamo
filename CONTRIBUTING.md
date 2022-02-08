If you have a question about Scanamo there is a 
[Gitter channel](https://gitter.im/guardian/scanamo) to try and 
answer it. Suggestions on how to improve the documentation are very
welcome.

Feel free to open an issue if you notice a bug or have an idea for a
feature. 

Pull requests are gladly accepted. Scanamo follows a standard
[fork and pull](https://help.github.com/articles/using-pull-requests/)
model for contributions via GitHub pull requests.

Building and testing Scanamo
----------------------------

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

Contributing documentation
--------------------------

The [website](http://www.scanamo.org) is built using 
[sbt-microsites](https://47deg.github.io/sbt-microsites/). To check 
documentation changes: 
 * Make sure [jekyll](https://jekyllrb.com/docs/installation/) is installed locally
 * run `makeMicrosite` from the root of SBT
 * run `jekyll serve --incremental --baseurl /` from `docs/target/site`
 * Load http://127.0.0.1:4000/

Releasing
---------

Creating a git tag is all that is required to trigger Travis to publish an artifact to Maven 
Central for both Scala 2.12 and Scala 2.13 once the build is complete. It will also attempt to update
the documentation website at http://www.scanamo.org/ with the latest scaladoc.
