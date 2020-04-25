scalaVersion in ThisBuild := "2.12.10"
crossScalaVersions in ThisBuild := Seq("2.12.10", "2.13.1")

val catsVersion = "2.1.1"
val catsEffectVersion = "2.1.2"
val zioVersion = "1.0.0-RC18-2"

lazy val stdOptions = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-target:jvm-1.8"
)

lazy val std2xOptions = Seq(
  "-Xfatal-warnings",
  "-language:higherKinds",
  "-language:existentials",
  "-language:implicitConversions",
  "-explaintypes",
  "-Yrangepos",
  "-Xfuture",
  "-Xlint:_,-type-parameter-shadow",
  "-Yno-adapted-args",
  "-Ypartial-unification",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
)

def extraOptions(scalaVersion: String) =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) =>
      Seq(
        "-opt-warnings",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_,imports",
        "-Ywarn-unused:imports",
        "-opt:l:inline",
        "-opt-inline-from:<source>"
      ) ++ std2xOptions
    case _ => Seq.empty
  }

val commonSettings = Seq(
  organization := "org.scanamo",
  organizationName := "Scanamo",
  startYear := Some(2019),
  homepage := Some(url("http://www.scanamo.org/")),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
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

val awsDynamoDB = "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.762"

lazy val refined = (project in file("refined"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-refined"
  )
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit"    %% "refined"   % "0.9.13",
      "org.scalatest" %% "scalatest" % "3.1.1" % Test
    )
  )
  .dependsOn(scanamo)

lazy val scanamo = (project in file("scanamo"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo"
  )
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "org.typelevel"  %% "cats-free" % catsVersion,
      "com.propensive" %% "magnolia"  % "0.12.7",
      // Use Joda for custom conversion example
      "org.joda"          % "joda-convert"              % "2.2.1"       % Provided,
      "joda-time"         % "joda-time"                 % "2.10.6"      % Test,
      "org.scalatest"     %% "scalatest"                % "3.1.1"       % Test,
      "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test,
      "org.scalacheck"    %% "scalacheck"               % "1.14.3"      % Test
    )
  )
  .dependsOn(testkit % "test->test")

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
      "org.typelevel"  %% "cats-free"   % catsVersion,
      "org.typelevel"  %% "cats-core"   % catsVersion,
      "org.typelevel"  %% "cats-effect" % catsEffectVersion,
      "io.monix"       %% "monix"       % "3.1.0" % Provided,
      "co.fs2"         %% "fs2-core"    % "2.3.0" % Provided,
      "io.monix"       %% "monix"       % "3.1.0" % Test,
      "co.fs2"         %% "fs2-core"    % "2.3.0" % Test,
      "org.scalatest"  %% "scalatest"   % "3.1.1" % Test,
      "org.scalacheck" %% "scalacheck"  % "1.14.3" % Test
    ),
    fork in Test := true,
    scalacOptions in (Compile, doc) += "-no-link-warnings"
  )
  .dependsOn(scanamo, testkit % "test->test")

lazy val zio = (project in file("zio"))
  .settings(
    name := "scanamo-zio",
    commonSettings,
    publishingSettings,
    libraryDependencies ++= List(
      awsDynamoDB,
      "org.typelevel"  %% "cats-core"        % catsVersion,
      "org.typelevel"  %% "cats-effect"      % catsEffectVersion,
      "dev.zio"        %% "zio"              % zioVersion,
      "dev.zio"        %% "zio-streams"      % zioVersion % Provided,
      "dev.zio"        %% "zio-interop-cats" % "2.0.0.0-RC11",
      "org.scalatest"  %% "scalatest"        % "3.1.1" % Test,
      "org.scalacheck" %% "scalacheck"       % "1.14.3" % Test
    ),
    fork in Test := true,
    scalacOptions in (Compile, doc) += "-no-link-warnings"
  )
  .dependsOn(scanamo, testkit % "test->test")

lazy val alpakka = (project in file("alpakka"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-alpakka"
  )
  .settings(
    libraryDependencies ++= Seq(
      awsDynamoDB,
      "org.typelevel"      %% "cats-free"                    % catsVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-dynamodb" % "1.1.2",
      "org.scalatest"      %% "scalatest"                    % "3.1.1" % Test,
      "org.scalacheck"     %% "scalacheck"                   % "1.14.3" % Test
    ),
    fork in Test := true,
    // unidoc can work out links to other project, but scalac can't
    scalacOptions in (Compile, doc) += "-no-link-warnings"
  )
  .dependsOn(scanamo, testkit % "test->test")

lazy val joda = (project in file("joda"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-joda"
  )
  .settings(
    libraryDependencies ++= List(
      "org.joda"       % "joda-convert" % "2.2.1" % Provided,
      "joda-time"      % "joda-time"    % "2.10.6",
      "org.scalatest"  %% "scalatest"   % "3.1.1" % Test,
      "org.scalacheck" %% "scalacheck"  % "1.14.3" % Test
    )
  )
  .dependsOn(scanamo)

lazy val docs = (project in file("docs"))
  .settings(
    commonSettings,
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
  publishArtifact in Test := false,
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
  micrositeCompilingDocsTool := WithMdoc,
  micrositePushSiteWith := GitHub4s,
  micrositeGithubToken := sys.env.get("GITHUB_TOKEN")
)
