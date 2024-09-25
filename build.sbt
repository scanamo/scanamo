import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion

Global / onChangedBuildSource := ReloadOnSourceChanges
val V = new {
  val scala212 = "2.12.19"
  val scala213 = "2.13.14"
  val scala3 = "3.3.3"
  val magnolia = "1.1.8"
  val magnoliaFor3 = "1.3.4"
  val catsVersion = "2.10.0"
  val catsEffectVersion = "3.5.4"
}

ThisBuild / scalaVersion := V.scala3

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19" % Test
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.18.0" % Test
val zioVersion = "1.0.18"

lazy val stdOptions = Seq(
  "-release:8",
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
        compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full)
      )
    case _ => Seq.empty
  })
)

lazy val kindProjectorSettings = Seq(
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
  startYear := Some(2019),
  homepage := Some(url("https://www.scanamo.org/")),
  crossScalaVersions := Seq(V.scala212, V.scala213, V.scala3),
  Test / scalacOptions := {
    val mainScalacOptions = scalacOptions.value
    (if (CrossVersion.partialVersion(scalaVersion.value).contains((2, 12)))
       mainScalacOptions.filter(!Set("-Ywarn-value-discard", "-Xlint").contains(_)) :+ "-Xlint:-unused,_"
     else
       mainScalacOptions).filter(_ != "-Xfatal-warnings")
  },
  Compile / console / scalacOptions := (Test / scalacOptions).value,
  autoAPIMappings := true,
  apiURL := Some(url("https://www.scanamo.org/latest/api/")),
  Test / parallelExecution := false
)

lazy val root = (project in file("."))
  .aggregate(scanamo, testkit, catsEffect, joda, zio, pekko)
  .settings(
    publish / skip := true,
    // releaseVersion := ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease().value,
    releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      setNextVersion,
      commitNextVersion
    ),
    dynamoDBLocalDownloadDir := file(".dynamodb-local"),
    dynamoDBLocalPort := 8042,
    startDynamoDBLocal / aggregate := false,
    dynamoDBLocalTestCleanup / aggregate := false,
    stopDynamoDBLocal / aggregate := false
  )

val awsDynamoDB = "software.amazon.awssdk" % "dynamodb" % "2.26.25"

lazy val scanamo = (project in file("scanamo"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo"
  )
  .settings(scala2settings)
  .settings(macroSettings)
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
      "org.typelevel"          %% "cats-free"          % V.catsVersion,
      // Use Joda for custom conversion example
      "org.joda"           % "joda-convert"    % "2.2.3"    % Provided,
      "joda-time"          % "joda-time"       % "2.12.7"   % Test,
      scalaTest,
      "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test,
      scalaCheck
    )
  )
  .dependsOn(testkit % "test->test")

lazy val testkit = (project in file("testkit"))
  .settings(
    commonSettings,
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
    publishingSettings,
    libraryDependencies ++= List(
      awsDynamoDB,
      "org.typelevel"  %% "cats-free"   % V.catsVersion,
      "org.typelevel"  %% "cats-core"   % V.catsVersion,
      "org.typelevel"  %% "cats-effect" % V.catsEffectVersion,
      "co.fs2"         %% "fs2-core"    % "3.10.2",
      scalaTest,
      scalaCheck
    ),
    Test / fork := true,
    Compile / doc / scalacOptions += "-no-link-warnings"
  )
  .settings(scala2settings)
  .settings(kindProjectorSettings)
  .dependsOn(scanamo, testkit % "test->test")

lazy val zio = (project in file("zio"))
  .settings(
    name := "scanamo-zio",
    commonSettings,
    publishingSettings,
    libraryDependencies ++= List(
      awsDynamoDB,
      "org.typelevel"  %% "cats-core"        % V.catsVersion,
      "org.typelevel"  %% "cats-effect"      % V.catsEffectVersion,
      "dev.zio"        %% "zio"              % zioVersion,
      "dev.zio"        %% "zio-streams"      % zioVersion % Provided,
      "dev.zio"        %% "zio-interop-cats" % "3.1.1.0",
      scalaTest,
      scalaCheck
    ),
    Test / fork := true,
    Compile / doc / scalacOptions += "-no-link-warnings"
  )
  .settings(scala2settings)
  .dependsOn(scanamo, testkit % "test->test")

lazy val pekko = (project in file("pekko"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-pekko"
  )
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "org.typelevel"    %% "cats-free"                 % V.catsVersion,
      "org.apache.pekko" %% "pekko-connectors-dynamodb" % "1.0.2",
      scalaTest,
      scalaCheck
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
    publishingSettings,
    name := "scanamo-joda"
  )
  .settings(
    libraryDependencies ++= List(
      "org.joda"        % "joda-convert" % "2.2.3"  % Provided,
      "joda-time"       % "joda-time"    % "2.12.7",
      scalaTest,
      scalaCheck
    )
  )
  .dependsOn(scanamo)

lazy val docs = project
  .in(file("scanamo-docs"))
  .settings(
    moduleName := "scanamo-docs",
    mdocOut := file("scanamo-website/docs"),
    mdocVariables := Map(
       "VERSION" -> version.value
    )
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .dependsOn(scanamo % "compile->test")

val publishingSettings = Seq(
  organization := "org.scanamo",
  organizationName := "Scanamo",
  scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
  licenses := Seq(License.Apache2),
  Test / publishArtifact := false
)
