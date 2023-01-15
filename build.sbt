Global / onChangedBuildSource := ReloadOnSourceChanges
val V = new {
  val scala212 = "2.12.16"
  val scala213 = "2.13.8"
  val scala3 = "3.2.0"
  val magnolia = "1.1.3"
  val magnoliaFor3 = "1.2.6"
  val catsVersion = "2.6.1"
  val catsEffectVersion = "3.3.12"
}
val scala2xVersions = Seq(V.scala212, V.scala213)
val allCrossVersions = Seq(V.scala212, V.scala213, V.scala3)

val zioVersion = "1.0.13"

lazy val stdOptions = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked"
)

lazy val std2_12Options = Seq(
  "-opt-warnings",
  "-Ywarn-extra-implicit",
  "-Ywarn-unused:_,imports",
  "-Ywarn-unused:imports",
  "-opt:l:inline",
  "-opt-inline-from:<source>",
  "-Xfatal-warnings", // lots of warnings against Scala 2.13 at the moment, so not enabling for 2.13
  "-Xfuture",
  "-Ypartial-unification",
  "-Yno-adapted-args",
  "-Ypartial-unification",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit"
)

lazy val std2xOptions = Seq(
  "-Xsource:3", // https://docs.scala-lang.org/scala3/guides/migration/tooling-tour.html#the-scala-213-compiler
  "-target:jvm-1.8",
  "-language:higherKinds",
  "-language:existentials",
  "-language:implicitConversions",
  "-explaintypes",
  "-Yrangepos",
  "-Xlint:_,-type-parameter-shadow",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
)

def extraOptions(scalaVersion: String) =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, scala2subVersion)) =>
      std2xOptions ++ (if (scala2subVersion == 12) std2_12Options else Seq.empty)
    case _ => Seq.empty
  }

lazy val scala2settings = Seq(
  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) =>
      Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
      )
    case _ => Seq.empty
  })
)

lazy val kindprojectorSettings = Seq(
  Compile / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq("-Ykind-projector:underscores")
      case Some((2, 12 | 13)) => Seq("-Xsource:3", "-P:kind-projector:underscore-placeholders")
      case _ => Seq.empty
    }
  },
)

lazy val macroSettings = Seq(
  libraryDependencies += (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => "com.softwaremill.magnolia1_2" %% "magnolia" % V.magnolia
    case _ => "com.softwaremill.magnolia1_3" %% "magnolia" % V.magnoliaFor3
  })
)
val commonSettings = Seq(
  organization := "org.scanamo",
  organizationName := "Scanamo",
  startYear := Some(2019),
  homepage := Some(url("http://www.scanamo.org/")),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
  Test / scalacOptions := {
    val mainScalacOptions = scalacOptions.value
    (if (CrossVersion.partialVersion(scalaVersion.value) == Some((2, 12)))
       mainScalacOptions.filter(!Seq("-Ywarn-value-discard", "-Xlint").contains(_)) :+ "-Xlint:-unused,_"
     else
       mainScalacOptions).filter(_ != "-Xfatal-warnings")
  },
  Compile / console / scalacOptions := (Test / scalacOptions).value,
  autoAPIMappings := true,
  apiURL := Some(url("http://www.scanamo.org/latest/api/")),
  dynamoDBLocalDownloadDir := file(".dynamodb-local"),
  dynamoDBLocalPort := 8042,
  Test / parallelExecution := false
)

lazy val root = (project in file("."))
  .aggregate(scanamo, testkit, alpakka, refined, catsEffect, joda, zio)
  .settings(
    commonSettings,
    publishingSettings,
    noPublishSettings,
    startDynamoDBLocal / aggregate := false,
    dynamoDBLocalTestCleanup / aggregate := false,
    stopDynamoDBLocal / aggregate := false
  )

addCommandAlias("makeMicrosite", "docs/makeMicrosite")
addCommandAlias("publishMicrosite", "docs/publishMicrosite")

val awsDynamoDB = "software.amazon.awssdk" % "dynamodb" % "2.17.272"

lazy val refined = (project in file("refined"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-refined",
    crossScalaVersions := scala2xVersions
  )
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit"    %% "refined"   % "0.9.29",
      "org.scalatest" %% "scalatest" % "3.2.9" % Test
    )
  )
  .dependsOn(scanamo)

lazy val scanamo = (project in file("scanamo"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo",
    crossScalaVersions := allCrossVersions
  )
  .settings(scala2settings)
  .settings(macroSettings)
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
      "org.typelevel"          %% "cats-free"          % V.catsVersion,
      // Use Joda for custom conversion example
      "org.joda"           % "joda-convert"    % "2.2.2"    % Provided,
      "joda-time"          % "joda-time"       % "2.11.1"   % Test,
      "org.scalatest"     %% "scalatest"       % "3.2.9"    % Test,
      "org.scalatestplus" %% "scalacheck-1-15" % "3.2.10.0" % Test,
      "org.scalacheck"    %% "scalacheck"      % "1.16.0"   % Test
    )
  )
  .dependsOn(testkit % "test->test")

lazy val testkit = (project in file("testkit"))
  .settings(
    commonSettings,
    crossScalaVersions := allCrossVersions,
    publishingSettings,
    name := "scanamo-testkit",
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
    )
  )

lazy val catsEffect = (project in file("cats"))
  .settings(
    name := "scanamo-cats-effect",
    commonSettings,
    crossScalaVersions := allCrossVersions,
    publishingSettings,
    libraryDependencies ++= List(
      awsDynamoDB,
      "org.typelevel"  %% "cats-free"   % V.catsVersion,
      "org.typelevel"  %% "cats-core"   % V.catsVersion,
      "org.typelevel"  %% "cats-effect" % V.catsEffectVersion,
      "co.fs2"         %% "fs2-core"    % "3.4.0",
      "org.scalatest"  %% "scalatest"   % "3.2.9"  % Test,
      "org.scalacheck" %% "scalacheck"  % "1.16.0" % Test
    ),
    Test / fork := true,
    Compile / doc / scalacOptions += "-no-link-warnings"
  )
  .settings(scala2settings)
  .settings(kindprojectorSettings)
  .dependsOn(scanamo, testkit % "test->test")

lazy val zio = (project in file("zio"))
  .settings(
    name := "scanamo-zio",
    commonSettings,
    crossScalaVersions := scala2xVersions,
    publishingSettings,
    libraryDependencies ++= List(
      awsDynamoDB,
      "org.typelevel"  %% "cats-core"        % V.catsVersion,
      "org.typelevel"  %% "cats-effect"      % V.catsEffectVersion,
      "dev.zio"        %% "zio"              % zioVersion,
      "dev.zio"        %% "zio-streams"      % zioVersion % Provided,
      "dev.zio"        %% "zio-interop-cats" % "3.1.1.0",
      "org.scalatest"  %% "scalatest"        % "3.2.9"    % Test,
      "org.scalacheck" %% "scalacheck"       % "1.16.0"   % Test
    ),
    Test / fork := true,
    Compile / doc / scalacOptions += "-no-link-warnings"
  )
  .settings(scala2settings)
  .dependsOn(scanamo, testkit % "test->test")

// Necessary until Alpakka uses Akka 2.6.16 or later - see https://github.com/akka/akka/pull/30375
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always

lazy val alpakka = (project in file("alpakka"))
  .settings(
    commonSettings,
    crossScalaVersions := scala2xVersions,
    publishingSettings,
    name := "scanamo-alpakka"
  )
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "org.typelevel"      %% "cats-free"                    % V.catsVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-dynamodb" % "2.0.2",
      "org.scalatest"      %% "scalatest"                    % "3.2.9"  % Test,
      "org.scalacheck"     %% "scalacheck"                   % "1.16.0" % Test
    ),
    Test / fork := true,
    // unidoc can work out links to other project, but scalac can't
    Compile / doc / scalacOptions += "-no-link-warnings"
  )
  .settings(scala2settings)
  .dependsOn(scanamo, testkit % "test->test")

lazy val joda = (project in file("joda"))
  .settings(
    commonSettings,
    crossScalaVersions := allCrossVersions,
    publishingSettings,
    name := "scanamo-joda"
  )
  .settings(
    libraryDependencies ++= List(
      "org.joda"        % "joda-convert" % "2.2.2"  % Provided,
      "joda-time"       % "joda-time"    % "2.12.0",
      "org.scalatest"  %% "scalatest"    % "3.2.15"  % Test,
      "org.scalacheck" %% "scalacheck"   % "1.17.0" % Test
    )
  )
  .dependsOn(scanamo)

lazy val docs = (project in file("docs"))
  .settings(
    commonSettings,
    crossScalaVersions := allCrossVersions,
    micrositeSettings,
    noPublishSettings,
    ghpagesNoJekyll := false,
    git.remoteRepo := "git@github.com:scanamo/scanamo.git",
    mdocVariables := Map(
      "VERSION" -> version.value
    )
  )
  .enablePlugins(MicrositesPlugin)
  .dependsOn(scanamo % "compile->test", alpakka % "compile", refined % "compile")

val publishingSettings = Seq(
  Test / publishArtifact := false,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/scanamo/scanamo"),
      "scm:git:git@github.com:scanamo/scanamo.git"
    )
  ),
  developers := List(
    Developer("philwills", "Phil Wills", "", url("https://github.com/philwills")),
    Developer(
      "regiskuckaertz",
      "Regis Kuckaertz",
      "regis.kuckaertz@theguardian.com",
      url("https://github.com/regiskuckaertz")
    )
  )
)

lazy val noPublishSettings = Seq(
  publish / skip := true
)

val micrositeSettings = Seq(
  micrositeUrl := "https://www.scanamo.org",
  micrositeName := "Scanamo",
  micrositeDescription := "Scanamo: simpler DynamoDB access for Scala",
  micrositeAuthor := "Scanamo Contributors",
  micrositeGithubOwner := "scanamo",
  micrositeGithubRepo := "scanamo",
  micrositeDocumentationUrl := "/latest/api",
  micrositeDocumentationLabelDescription := "API",
  micrositeHighlightTheme := "monokai",
  micrositeHighlightLanguages ++= Seq("sbt"),
  micrositeGitterChannel := false,
  micrositeShareOnSocial := false,
  micrositePalette := Map(
    "brand-primary" -> "#951c55",
    "brand-secondary" -> "#005689",
    "brand-tertiary" -> "#00456e",
    "gray-dark" -> "#453E46",
    "gray" -> "#837F84",
    "gray-light" -> "#E3E2E3",
    "gray-lighter" -> "#F4F3F4",
    "white-color" -> "#FFFFFF"
  ),
  micrositePushSiteWith := GitHub4s,
  micrositeGithubToken := sys.env.get("GITHUB_TOKEN")
)
