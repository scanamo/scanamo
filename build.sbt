scalaVersion in ThisBuild := "2.12.4"
crossScalaVersions in ThisBuild := Seq("2.11.11", scalaVersion.value)

val catsVersion = "1.1.0"

val commonSettings =  Seq(
  organization := "com.gu",
  scalacOptions := Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Ypartial-unification"
  ),

  // for simulacrum
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),

  // sbt-doctest leaves some unused values
  // see https://github.com/scala/bug/issues/10270
  scalacOptions in Test := {
    val mainScalacOptions = scalacOptions.value
    if (CrossVersion.partialVersion(scalaVersion.value) == Some((2,12)))
      mainScalacOptions.filter(!Seq("-Ywarn-value-discard", "-Xlint").contains(_)) :+ "-Xlint:-unused,_"
    else
      mainScalacOptions
  },
  scalacOptions in (Compile, console) := (scalacOptions in Test).value,
  autoAPIMappings := true,
  apiURL := Some(url("http://www.scanamo.org/latest/api/")),
)

val dynamoTestSettings = Seq(
  dynamoDBLocalDownloadDir := file(".dynamodb-local"),
  dynamoDBLocalPort := 8042,

  startDynamoDBLocal := startDynamoDBLocal.dependsOn(compile in Test).value,
  test in Test := (test in Test).dependsOn(startDynamoDBLocal).value,
  testOptions in Test += dynamoDBLocalTestCleanup.value,

  parallelExecution in Test := false
)

lazy val root = (project in file("."))
  .aggregate(formats, scanamo, alpakka, refined)
  .settings(
    commonSettings,
    publishingSettings,
    noPublishSettings,
  )

addCommandAlias("tut", "docs/tut")
addCommandAlias("makeMicrosite", "docs/makeMicrosite")
addCommandAlias("publishMicrosite", "docs/publishMicrosite")

lazy val formats = (project in file("formats"))
  .settings(
    commonSettings,
    publishingSettings,

    name := "scanamo-formats",
  )
  .settings(

    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.256",
      "com.propensive" %% "magnolia" % "0.7.1",
      "com.github.mpilquist" %% "simulacrum" % "0.11.0",
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    ),

    doctestMarkdownEnabled := true,
    doctestDecodeHtmlEntities := true,
    doctestTestFramework := DoctestTestFramework.ScalaTest
  )

lazy val refined = (project in file("refined"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-refined"
  )
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit" %% "refined" % "0.8.6",
      "org.scalatest" %% "scalatest" % "3.0.4" % Test
    )
  )
  .dependsOn(formats)

lazy val scanamo = (project in file("scanamo"))
  .settings(
    commonSettings,
    publishingSettings,
    dynamoTestSettings,

    name := "scanamo"
  )
  .settings(

    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.256",
      "com.propensive" %% "magnolia" % "0.7.1",
      "org.typelevel" %% "cats-free" % catsVersion,
      "com.github.mpilquist" %% "simulacrum" % "0.11.0",

      // Use Joda for custom conversion example
      "org.joda" % "joda-convert" % "1.8.3" % Provided,
      "joda-time" % "joda-time" % "2.9.9" % Test,

      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    )
  )
  .dependsOn(formats)

lazy val alpakka = (project in file("alpakka"))
  .settings(
    commonSettings,
    publishingSettings,
    dynamoTestSettings,

    name := "scanamo-alpakka",
  )
  .settings(

    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.256",
      "org.typelevel" %% "cats-free" % catsVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-dynamodb" % "0.15.1",

      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    ),

    fork in Test := true,
    envVars in Test := Map(
      "AWS_ACCESS_KEY_ID" -> "dummy",
      "AWS_SECRET_KEY" -> "credentials"
    ),
    dynamoDBLocalDownloadDir := file(".alpakka-dynamodb-local"),
    dynamoDBLocalPort := 8052,

    // unidoc can work out links to other project, but scalac can't
    scalacOptions in (Compile, doc) += "-no-link-warnings",
  )
  .dependsOn(formats, scanamo)

lazy val docs = (project in file("docs"))
  .settings(
    commonSettings,
    micrositeSettings,
    noPublishSettings,

    dynamoDBLocalDownloadDir := file(".dynamodb-local"),
    dynamoDBLocalPort := 8042,

    tut := tut.dependsOn(startDynamoDBLocal).value,
    stopDynamoDBLocal := stopDynamoDBLocal.triggeredBy(tut).value,

    includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.yml",
    ghpagesNoJekyll := false,
    git.remoteRepo := "git@github.com:scanamo/scanamo.git",

    makeMicrosite := makeMicrosite.dependsOn(unidoc in Compile).value,
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc),
    siteSubdirName in ScalaUnidoc := "latest/api",
  )
  .enablePlugins(MicrositesPlugin, SiteScaladocPlugin, GhpagesPlugin, ScalaUnidocPlugin)
  .disablePlugins(ReleasePlugin)
  .dependsOn(scanamo % "compile->test", alpakka % "compile", refined % "compile")


import ReleaseTransformations._
val publishingSettings = Seq(
  homepage := Some(url("http://www.scanamo.org/")),
  licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  scmInfo := Some(ScmInfo(
    url("https://github.com/scanamo/scanamo"),
    "scm:git:git@github.com:scanamo/scanamo.git"
  )),

  pomExtra := {
    <developers>
      <developer>
        <id>philwills</id>
        <name>Phil Wills</name>
        <url>https://github.com/philwills</url>
      </developer>
    </developers>
  },

  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),

  releaseCrossBuild := true,

  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    releaseStepCommandAndRemaining("+docs/tut"),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("+publishSigned"),
    setNextVersion,
    commitNextVersion,
    releaseStepCommandAndRemaining("+sonatypeReleaseAll"),
    pushChanges,
    releaseStepCommandAndRemaining("publishMicrosite"),
  )
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

val micrositeSettings = Seq(
  micrositeName             := "Scanamo",
  micrositeDescription      := "Scanamo: simpler DynamoDB access for Scala",
  micrositeAuthor           := "Scanamo Contributors",
  micrositeGithubOwner      := "scanamo",
  micrositeGithubRepo       := "scanamo",
  micrositeBaseUrl          := "",
  micrositeDocumentationUrl := "/latest/api",
  micrositeHighlightTheme   := "color-brewer",
  micrositePalette := Map(
    "brand-primary"     -> "#951c55",
    "brand-secondary"   -> "#005689",
    "brand-tertiary"    -> "#00456e",
    "gray-dark"         -> "#453E46",
    "gray"              -> "#837F84",
    "gray-light"        -> "#E3E2E3",
    "gray-lighter"      -> "#F4F3F4",
    "white-color"       -> "#FFFFFF"
  )
)
