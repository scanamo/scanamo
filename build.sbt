name := "scanamo"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	"com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.43",
  "com.chuusai" %% "shapeless" % "2.2.5",

  "com.github.mpilquist" %% "simulacrum" % "0.5.0",
  compilerPlugin("org.scalamacros" %% "paradise" % "2.1.0-M5" cross CrossVersion.full),
  "org.typelevel" %% "discipline" % "0.4",
	"org.scalatest" %% "scalatest" % "2.2.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.12.4" % Test
)

tutSettings
site.settings
site.addMappingsToSiteDir(tut, "")
site.includeScaladoc()
ghpages.settings
com.typesafe.sbt.SbtGhPages.GhPagesKeys.ghpagesNoJekyll := false
git.remoteRepo := "git@github.com:guardian/scanamo.git"

doctestSettings
doctestTestFramework := DoctestTestFramework.ScalaTest

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