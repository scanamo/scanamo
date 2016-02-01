name := "scanamo"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	"com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.43",
	"com.github.mpilquist" %% "simulacrum" % "0.5.0",
  compilerPlugin("org.scalamacros" %% "paradise" % "2.1.0-M5" cross CrossVersion.full),
  "org.typelevel" %% "discipline" % "0.4",
	"org.scalatest" %% "scalatest" % "2.2.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.12.4" % Test
)

doctestSettings
doctestTestFramework := DoctestTestFramework.ScalaTest