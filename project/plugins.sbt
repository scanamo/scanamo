addSbtPlugin("com.localytics" % "sbt-dynamodb" % "2.0.0")

addSbtPlugin("com.github.tkawachi" % "sbt-doctest" % "0.7.0")

resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.1")
addSbtPlugin("com.47deg" % "sbt-microsites" % "0.7.2")
addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.6.1")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

// Not available for SBT 1.0
//addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")
