logLevel := Level.Warn

resolvers ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/",
  Resolver.url("lustefaniak/sbt-plugins", url("https://dl.bintray.com/lustefaniak/sbt-plugins/"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("io.get-coursier"      % "sbt-coursier"          % "1.0.2")
addSbtPlugin("com.dwijnand"         % "sbt-dynver"            % "3.3.0")
addSbtPlugin("org.foundweekends"    % "sbt-bintray"           % "0.5.4")