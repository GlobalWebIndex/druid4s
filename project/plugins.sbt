logLevel := Level.Warn

resolvers ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/",
  Resolver.url("lustefaniak/sbt-plugins", url("https://dl.bintray.com/lustefaniak/sbt-plugins/"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("io.get-coursier"      % "sbt-coursier"          % "1.0.2")
addSbtPlugin("com.dwijnand"         % "sbt-dynver"            % "3.0.0")
addSbtPlugin("com.frugalmechanic"   % "fm-sbt-s3-resolver"    % "0.16.0")