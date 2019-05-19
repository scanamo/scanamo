addSbtPlugin("com.localytics" % "sbt-dynamodb" % "2.0.3")

addSbtPlugin("com.github.tkawachi" % "sbt-doctest" % "0.9.2")

resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages"    % "0.6.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-site"       % "1.3.2")
addSbtPlugin("com.47deg"        % "sbt-microsites" % "0.7.23")
addSbtPlugin("org.tpolecat"     % "tut-plugin"     % "0.6.11")
addSbtPlugin("com.eed3si9n"     % "sbt-unidoc"     % "0.4.2")
addSbtPlugin("com.geirsson"     % "sbt-ci-release" % "1.2.2")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"  % "1.5.1")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"   % "2.0.0")
