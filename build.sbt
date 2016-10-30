name := "scanamo"
organization := "com.gu"

scalaVersion := "2.12.0"

crossScalaVersions := Seq("2.11.8", scalaVersion.value)

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.52",
  "com.chuusai" %% "shapeless" % "2.3.2",
  "org.typelevel" %% "cats-free" % "0.8.1",
  "com.github.mpilquist" %% "simulacrum" % "0.10.0",

  "org.typelevel" %% "macro-compat" % "1.1.1",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
  compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch),

  // Use Joda for custom conversion example
  "org.joda" % "joda-convert" % "1.8.1" % Provided,
  "joda-time" % "joda-time" % "2.9.5" % Test,

  "org.scalatest" %% "scalatest" % "3.0.0" % Test,
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
  "-Ywarn-value-discard"
)

dynamoDBLocalDownloadDir := file(".dynamodb-local")
dynamoDBLocalPort := 8042
startDynamoDBLocal := startDynamoDBLocal.dependsOn(compile in Test).value
test in Test := (test in Test).dependsOn(startDynamoDBLocal).value
testOptions in Test += dynamoDBLocalTestCleanup.value

tut <<= tut.dependsOn(startDynamoDBLocal)
testOptions in Test <+= dynamoDBLocalTestCleanup

enablePlugins(MicrositesPlugin)

includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.yml"
ghpages.settings
com.typesafe.sbt.SbtGhPages.GhPagesKeys.ghpagesNoJekyll := false
git.remoteRepo := "git@github.com:guardian/scanamo.git"

doctestMarkdownEnabled := true
doctestDecodeHtmlEntities := true
doctestWithDependencies := false
doctestTestFramework := DoctestTestFramework.ScalaTest

parallelExecution in Test := false

homepage := Some(url("https://github.com/guardian/scanamo"))
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
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _)),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
  pushChanges
)
