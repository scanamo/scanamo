ThisBuild / scalaVersion := "2.12.15"
ThisBuild / crossScalaVersions := Seq("2.12.15", "2.13.6")

val catsVersion = "2.6.1"

val catsEffectVersion = "3.3.2"

val zioVersion = "1.0.12"

lazy val stdOptions = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked"
)

lazy val std2xOptions = Seq(
  "-Xsource:3", // https://docs.scala-lang.org/scala3/guides/migration/tooling-tour.html#the-scala-213-compiler
  "-target:jvm-1.8",
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
  Test / parallelExecution := false,
  Compile / unmanagedSourceDirectories ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          file(sourceDirectory.value.getPath + "/main/scala-2.x")
        )
      case _ =>
        Nil
    }
  },
  Test / unmanagedSourceDirectories ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          file(sourceDirectory.value.getPath + "/test/scala-2.x")
        )
      case _ =>
        Nil
    }
  }
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

val awsDynamoDB = "software.amazon.awssdk" % "dynamodb" % "2.17.92"

lazy val refined = (project in file("refined"))
  .settings(
    commonSettings,
    publishingSettings,
    name := "scanamo-refined"
  )
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit"    %% "refined"   % "0.9.28",
      "org.scalatest" %% "scalatest" % "3.2.9" % Test
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
      "org.scala-lang.modules"       %% "scala-java8-compat" % "1.0.2",
      "org.typelevel"                %% "cats-free"          % catsVersion,
      "com.softwaremill.magnolia1_2" %% "magnolia"           % "1.0.0",
      "org.scala-lang"                % "scala-reflect"      % scalaVersion.value,
      // Use Joda for custom conversion example
      "org.joda"           % "joda-convert"    % "2.2.1"    % Provided,
      "joda-time"          % "joda-time"       % "2.10.13"  % Test,
      "org.scalatest"     %% "scalatest"       % "3.2.9"    % Test,
      "org.scalatestplus" %% "scalacheck-1-15" % "3.2.10.0" % Test,
      "org.scalacheck"    %% "scalacheck"      % "1.15.4"   % Test
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
      "org.typelevel"  %% "cats-free"   % catsVersion,
      "org.typelevel"  %% "cats-core"   % catsVersion,
      "org.typelevel"  %% "cats-effect" % catsEffectVersion,
      "co.fs2"         %% "fs2-core"    % "3.2.3",
      "org.scalatest"  %% "scalatest"   % "3.2.9"  % Test,
      "org.scalacheck" %% "scalacheck"  % "1.15.4" % Test
    ),
    Test / fork := true,
    Compile / doc / scalacOptions += "-no-link-warnings"
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
      "dev.zio"        %% "zio-interop-cats" % "3.1.1.0",
      "org.scalatest"  %% "scalatest"        % "3.2.9"    % Test,
      "org.scalacheck" %% "scalacheck"       % "1.15.4"   % Test
    ),
    Test / fork := true,
    Compile / doc / scalacOptions += "-no-link-warnings"
  )
  .dependsOn(scanamo, testkit % "test->test")

// Necessary until Alpakka uses Akka 2.6.16 or later - see https://github.com/akka/akka/pull/30375
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always

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
      "com.lightbend.akka" %% "akka-stream-alpakka-dynamodb" % "2.0.2",
      "org.scalatest"      %% "scalatest"                    % "3.2.9"  % Test,
      "org.scalacheck"     %% "scalacheck"                   % "1.15.4" % Test
    ),
    Test / fork := true,
    // unidoc can work out links to other project, but scalac can't
    Compile / doc / scalacOptions += "-no-link-warnings"
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
      "org.joda"        % "joda-convert" % "2.2.1"  % Provided,
      "joda-time"       % "joda-time"    % "2.10.13",
      "org.scalatest"  %% "scalatest"    % "3.2.9"  % Test,
      "org.scalacheck" %% "scalacheck"   % "1.15.4" % Test
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
