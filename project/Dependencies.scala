import sbt._

object Dependencies {

  val akkaVersion       = "2.5.26"
  val randagenVersion   = "0.2.7"
  val jacksonVersion    = "2.9.10"

  lazy val randagen                     = "net.globalwebindex"            %%    "randagen-core"                      % randagenVersion % "test"
  lazy val loggingImplLogback           = "ch.qos.logback"                %     "logback-classic"                    % "1.2.3"
  lazy val scalaHttp                    = "org.scalaj"                    %%    "scalaj-http"                        % "2.4.1"
  lazy val scalatest                    = "org.scalatest"                 %%    "scalatest"                          % "3.0.8"         % "test"

  lazy val loggingApi                   = Seq(
    "org.slf4j"                     %     "slf4j-api"                             % "1.7.28",
    "com.typesafe.scala-logging"    %%    "scala-logging"                         % "3.9.2"
  )

  lazy val jodaTime                     = Seq(
    "joda-time"                     %     "joda-time"                             % "2.10.5",
    "org.joda"                      %     "joda-convert"                          % "2.2.1"
  )

  lazy val jackson                      = Seq(
    "com.fasterxml.jackson.module"  %%    "jackson-module-scala"                  % jacksonVersion,
    "com.fasterxml.jackson.core"    %     "jackson-core"                          % jacksonVersion,
    "com.fasterxml.jackson.core"    %     "jackson-annotations"                   % jacksonVersion
  )

}
