addSbtPlugin("com.localytics"    % "sbt-dynamodb"              % "2.0.3")
addSbtPlugin("com.47deg"         % "sbt-microsites"            % "1.4.4")
addSbtPlugin("com.github.sbt"    % "sbt-ci-release"            % "1.5.12")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"             % "2.0.9")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"              % "2.5.2")
addSbtPlugin("ch.epfl.scala"     % "sbt-bloop"                 % "1.5.13")
addSbtPlugin("com.github.cb372"  % "sbt-explicit-dependencies" % "0.3.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header"                % "5.10.0")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
