scalaVersion in ThisBuild := "2.12.7"
crossScalaVersions in ThisBuild := Seq("2.11.12", scalaVersion.value)

val catsVersion = "1.3.1"
val catsEffectVersion = "1.0.0"
val scalazVersion = "7.2.25" // Bump as needed for io-effect compat
val scalazIOEffectVersion = "2.10.1"
val shimsVersion = "1.3.0"
val zioVersion = "0.3.1"

val commonSettings = Seq(
  scalacOptions := Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-Xfatal-warnings",
    "-Xlint:_",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Ypartial-unification"
  ),
  // for simulacrum
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
  // sbt-doctest leaves some unused values
  // see https://github.com/scala/bug/issues/10270
  scalacOptions in Test := {
    val mainScalacOptions = scalacOptions.value
    (if (CrossVersion.partialVersion(scalaVersion.value) == Some((2, 12)))
       mainScalacOptions.filter(!Seq("-Ywarn-value-discard", "-Xlint").contains(_)) :+ "-Xlint:-unused,_"
     else
       mainScalacOptions).filter(_ != "-Xfatal-warnings")
  },
  scalacOptions in (Compile, console) := (scalacOptions in Test).value,
  autoAPIMappings := true,
  apiURL := Some(url("http://www.scanamo.org/latest/api/")),
  dynamoDBLocalDownloadDir := file(".dynamodb-local"),
  dynamoDBLocalPort := 8042,
  Test / parallelExecution := false
)

lazy val root = (project in file("."))
  .aggregate(formats, scanamo, testkit, alpakka, refined, scalaz, catsEffect, javaTime, joda, zio)
  .settings(
    commonSettings,
    publishingSettings,
    noPublishSettings,
    startDynamoDBLocal / aggregate := false,
    dynamoDBLocalTestCleanup / aggregate := false,
    stopDynamoDBLocal / aggregate := false
  )

addCommandAlias("tut", "docs/tut")
addCommandAlias("makeMicrosite", "docs/makeMicrosite")
addCommandAlias("publishMicrosite", "docs/publishMicrosite")

val awsDynamoDB = "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.256"

lazy val formats = (project in file("formats"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-formats"
  )
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "com.chuusai" %% "shapeless" % "2.3.3",
      "com.github.mpilquist" %% "simulacrum" % "0.11.0",
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.scalatest" %% "scalatest" % "3.0.5" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    ),
    doctestMarkdownEnabled := true,
    doctestDecodeHtmlEntities := true,
    doctestTestFramework := DoctestTestFramework.ScalaTest
  )
  .dependsOn(testkit % "test->test")

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
    name := "scanamo"
  )
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "com.chuusai" %% "shapeless" % "2.3.3",
      "org.typelevel" %% "cats-free" % catsVersion,
      "com.github.mpilquist" %% "simulacrum" % "0.11.0",
      // Use Joda for custom conversion example
      "org.joda" % "joda-convert" % "1.8.3" % Provided,
      "joda-time" % "joda-time" % "2.9.9" % Test,
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    )
  )
  .dependsOn(formats, testkit % "test->test")

lazy val testkit = (project in file("testkit"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-testkit",
    libraryDependencies ++= Seq(
      awsDynamoDB
    )
  )

lazy val catsEffect = (project in file("cats"))
  .settings(
    name := "scanamo-cats-effect",
    commonSettings,
    publishingSettings,
    libraryDependencies ++= List(
      awsDynamoDB,
      "org.typelevel" %% "cats-free" % catsVersion,
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    ),
    fork in Test := true,
    scalacOptions in (Compile, doc) += "-no-link-warnings"
  )
  .dependsOn(formats, scanamo, testkit % "test->test")

lazy val scalaz = (project in file("scalaz"))
  .settings(
    name := "scanamo-scalaz",
    commonSettings,
    publishingSettings,
    libraryDependencies ++= List(
      awsDynamoDB,
      "com.codecommit" %% "shims" % shimsVersion,
      "org.scalaz" %% "scalaz-core" % scalazVersion,
      "org.scalaz" %% "scalaz-ioeffect" % scalazIOEffectVersion,
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    ),
    fork in Test := true,
    scalacOptions in (Compile, doc) += "-no-link-warnings"
  )
  .dependsOn(formats, scanamo, testkit % "test->test")

lazy val zio = (project in file("scalaz-zio"))
  .settings(
    name := "scanamo-scalaz-zio",
    commonSettings,
    publishingSettings,
    libraryDependencies ++= List(
      awsDynamoDB,
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.scalaz" %% "scalaz-zio" % zioVersion,
      "org.scalaz" %% "scalaz-zio-interop" % zioVersion,
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
    ),
    fork in Test := true,
    scalacOptions in (Compile, doc) += "-no-link-warnings"
  )
  .dependsOn(formats, scanamo, testkit % "test->test")

lazy val alpakka = (project in file("alpakka"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-alpakka"
  )
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "org.typelevel" %% "cats-free" % catsVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-dynamodb" % "0.20",
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test
    ),
    fork in Test := true,
    // unidoc can work out links to other project, but scalac can't
    scalacOptions in (Compile, doc) += "-no-link-warnings"
  )
  .dependsOn(formats, scanamo, testkit % "test->test")

lazy val javaTime = (project in file("java-time"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-time"
  )
  .settings(
    libraryDependencies ++= List(
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
      "com.47deg" %% "scalacheck-toolbox-datetime" % "0.2.4" % Test
    )
  )
  .dependsOn(formats)

lazy val joda = (project in file("joda"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-joda"
  )
  .settings(
    libraryDependencies ++= List(
      "org.joda" % "joda-convert" % "1.8.3" % Provided,
      "joda-time" % "joda-time" % "2.9.9",
      "org.scalatest" %% "scalatest" % "3.0.4" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
      "com.47deg" %% "scalacheck-toolbox-datetime" % "0.2.4" % Test
    )
  )
  .dependsOn(formats)

lazy val docs = (project in file("docs"))
  .settings(
    commonSettings,
    micrositeSettings,
    noPublishSettings,
    includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.yml",
    ghpagesNoJekyll := false,
    git.remoteRepo := "git@github.com:scanamo/scanamo.git",
    makeMicrosite := makeMicrosite.dependsOn(unidoc in Compile).value,
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc),
    siteSubdirName in ScalaUnidoc := "latest/api"
  )
  .enablePlugins(MicrositesPlugin, SiteScaladocPlugin, GhpagesPlugin, ScalaUnidocPlugin)
  .dependsOn(scanamo % "compile->test", alpakka % "compile", refined % "compile")

val publishingSettings = Seq(
  organization := "org.scanamo",
  homepage := Some(url("http://www.scanamo.org/")),
  licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  publishArtifact in Test := false,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/scanamo/scanamo"),
      "scm:git:git@github.com:scanamo/scanamo.git"
    )
  ),
  developers := List(
    Developer("philwills", "Phil Wills", "", url("https://github.com/philwills")),
    Developer("regiskuckaertz", "Regis Kuckaertz", "regis.kuckaertz@theguardian.com", url("https://github.com/regiskuckaertz"))
  )
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

val micrositeSettings = Seq(
  micrositeName := "Scanamo",
  micrositeDescription := "Scanamo: simpler DynamoDB access for Scala",
  micrositeAuthor := "Scanamo Contributors",
  micrositeGithubOwner := "scanamo",
  micrositeGithubRepo := "scanamo",
  micrositeBaseUrl := "",
  micrositeDocumentationUrl := "/latest/api",
  micrositeHighlightTheme := "color-brewer",
  micrositePalette := Map(
    "brand-primary" -> "#951c55",
    "brand-secondary" -> "#005689",
    "brand-tertiary" -> "#00456e",
    "gray-dark" -> "#453E46",
    "gray" -> "#837F84",
    "gray-light" -> "#E3E2E3",
    "gray-lighter" -> "#F4F3F4",
    "white-color" -> "#FFFFFF"
  )
)
