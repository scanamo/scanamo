scalaVersion in ThisBuild := "2.12.3"
crossScalaVersions in ThisBuild := Seq("2.11.11", scalaVersion.value)

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
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),

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
  apiURL := Some(url("https://guardian.github.io/scanamo/latest/api/")),
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
  .aggregate(formats, scanamo, alpakka)
  .settings(
    commonSettings,
    siteSubdirName in ScalaUnidoc := "latest/api",

    publishArtifact := false,
  )
  .enablePlugins(ScalaUnidocPlugin)

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
      "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.190",
      "com.chuusai" %% "shapeless" % "2.3.2",
      "com.github.mpilquist" %% "simulacrum" % "0.11.0",
      "org.typelevel" %% "cats-core" % "1.0.0-MF",

      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
    ),

    doctestMarkdownEnabled := true,
    doctestDecodeHtmlEntities := true,
    doctestTestFramework := DoctestTestFramework.ScalaTest
  )

lazy val scanamo = (project in file("scanamo"))
  .settings(
    commonSettings,
    publishingSettings,
    dynamoTestSettings,

    name := "scanamo"
  )
  .settings(

    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.190",
      "com.chuusai" %% "shapeless" % "2.3.2",
      "org.typelevel" %% "cats-free" % "1.0.0-MF",
      "com.github.mpilquist" %% "simulacrum" % "0.11.0",

      // Use Joda for custom conversion example
      "org.joda" % "joda-convert" % "1.8.3" % Provided,
      "joda-time" % "joda-time" % "2.9.9" % Test,

      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
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
      "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.190",
      "org.typelevel" %% "cats-free" % "1.0.0-MF",
      "com.lightbend.akka" %% "akka-stream-alpakka-dynamodb" % "0.11",

      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
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

    dynamoDBLocalDownloadDir := file(".dynamodb-local"),
    dynamoDBLocalPort := 8042,
    
    tut := tut.dependsOn(startDynamoDBLocal).value,
    stopDynamoDBLocal := stopDynamoDBLocal.triggeredBy(tut).value,

    includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.yml",
    ghpagesNoJekyll := false,
    git.remoteRepo := "git@github.com:guardian/scanamo.git",

    makeMicrosite := makeMicrosite.dependsOn(unidoc in Compile in root).value,
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc) in root, siteSubdirName in ScalaUnidoc in root),

    publishArtifact := false,
  )
  .enablePlugins(MicrositesPlugin, SiteScaladocPlugin, GhpagesPlugin)
  .dependsOn(scanamo % "compile->test", alpakka % "compile")


import ReleaseTransformations._
val publishingSettings = Seq(
  homepage := Some(url("https://guardian.github.io/scanamo/")),
  licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  scmInfo := Some(ScmInfo(
    url("https://github.com/guardian/scanamo"),
    "scm:git:git@github.com:guardian/scanamo.git"
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

  releaseCrossBuild := true,

  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    ReleaseStep(releaseStepTask(tut), enableCrossBuild = true),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    ReleaseStep(action = state => "publishSigned" :: state, enableCrossBuild = true),
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = state => "sonatypeReleaseAll" :: state, enableCrossBuild = true),
    pushChanges,
    releaseStepTask(publishMicrosite)
  )
)

val micrositeSettings = Seq(
  micrositeName             := "Scanamo",
  micrositeDescription      := "Scanamo: simpler DynamoDB access for Scala",
  micrositeAuthor           := "Scanamo Contributors",
  micrositeGithubOwner      := "guardian",
  micrositeGithubRepo       := "scanamo",
  micrositeBaseUrl          := "scanamo",
  micrositeDocumentationUrl := "/scanamo/latest/api",
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
