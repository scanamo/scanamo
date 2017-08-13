name := "scanamo"
organization := "com.gu"

scalaVersion := "2.12.1"

crossScalaVersions := Seq("2.11.11", scalaVersion.value)

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.154",
  "com.chuusai" %% "shapeless" % "2.3.2",
  "org.typelevel" %% "cats-free" % "1.0.1",
  "com.github.mpilquist" %% "simulacrum" % "0.10.0",

  "org.typelevel" %% "macro-compat" % "1.1.1",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
  compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch),

  // Use Joda for custom conversion example
  "org.joda" % "joda-convert" % "1.8.1" % Provided,
  "joda-time" % "joda-time" % "2.9.7" % Test,

  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)
// for simulacrum
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

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
)

dynamoDBLocalDownloadDir := file(".dynamodb-local")
dynamoDBLocalPort := 8042
startDynamoDBLocal := startDynamoDBLocal.dependsOn(compile in Test).value
test in Test := (test in Test).dependsOn(startDynamoDBLocal).value
testOptions in Test += dynamoDBLocalTestCleanup.value

tut := tut.dependsOn(startDynamoDBLocal).value

tut <<= (tut, stopDynamoDBLocal){ (tut, stop) => tut.doFinally(stop)}

enablePlugins(MicrositesPlugin, SiteScaladocPlugin)

includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.yml"
ghpages.settings
com.typesafe.sbt.SbtGhPages.GhPagesKeys.ghpagesNoJekyll := false
git.remoteRepo := "git@github.com:guardian/scanamo.git"

doctestMarkdownEnabled := true
doctestDecodeHtmlEntities := true
doctestWithDependencies := false
doctestTestFramework := DoctestTestFramework.ScalaTest

parallelExecution in Test := false

homepage := Some(url("https://guardian.github.io/scanamo/"))
licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))
publishMavenStyle := true
publishArtifact in Test := false
scmInfo := Some(ScmInfo(
  url("https://github.com/guardian/scanamo"),
  "scm:git:git@github.com:guardian/scanamo.git"
))

pomExtra := {
  <developers>
    <developer>
      <id>philwills</id>
      <name>Phil Wills</name>
      <url>https://github.com/philwills</url>
    </developer>
  </developers>
}

import ReleaseTransformations._

releaseCrossBuild := true

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  ReleaseStep(releaseStepTask(tut), enableCrossBuild = true),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
  pushChanges,
  releaseStepTask(publishMicrosite)
)

micrositeName             := "Scanamo"
micrositeDescription      := "Scanamo: simpler DynamoDB access for Scala"
micrositeAuthor           := "Scanamo Contributors"
micrositeGithubOwner      := "guardian"
micrositeGithubRepo       := "scanamo"
micrositeBaseUrl          := "scanamo"
micrositeDocumentationUrl := "/scanamo/latest/api"
micrositeHighlightTheme   := "color-brewer"
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
