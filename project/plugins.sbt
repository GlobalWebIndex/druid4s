logLevel := Level.Warn

resolvers ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/"
)

addSbtPlugin("com.dwijnand"         % "sbt-dynver"            % "3.3.0")
addSbtPlugin("org.scalameta"        % "sbt-scalafmt"          % "2.4.0")