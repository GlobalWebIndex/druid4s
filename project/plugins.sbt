logLevel := Level.Warn

resolvers ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/",
  Resolver.url("lustefaniak/sbt-plugins", url("https://dl.bintray.com/lustefaniak/sbt-plugins/"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("com.dwijnand"         % "sbt-dynver"            % "3.3.0")
addSbtPlugin("org.foundweekends"    % "sbt-bintray"           % "0.5.4")
addSbtPlugin("org.scalameta"        % "sbt-scalafmt"          % "2.4.0")