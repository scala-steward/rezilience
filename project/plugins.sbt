resolvers += "OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.5.2")
addSbtPlugin("org.typelevel"      % "sbt-tpolecat"             % "0.5.1")
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"             % "2.2.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.16.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("io.shiftleft"       % "sbt-ci-release-early"     % "2.0.46")
addSbtPlugin("com.47deg"          % "sbt-microsites"           % "1.4.4")
addSbtPlugin("com.github.sbt"     % "sbt-unidoc"               % "0.5.0")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
addSbtPlugin("com.github.sbt" % "sbt-pgp"        % "2.2.1")
addSbtPlugin("com.github.sbt" % "sbt-dynver"     % "5.0.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype"   % "3.11.1")
