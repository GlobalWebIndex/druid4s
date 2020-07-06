import java.util.TimeZone

import Dependencies._
import Deploy._

crossScalaVersions in ThisBuild := Seq("2.12.11", "2.11.8")
organization in ThisBuild := "net.globalwebindex"
libraryDependencies in ThisBuild ++= loggingApi

initialize := {
  System.setProperty("user.timezone", "UTC")
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
}

resolvers in ThisBuild ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/",
  Resolver.bintrayRepo("l15k4", "GlobalWebIndex")
)
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))
cancelable in ThisBuild := true
publishArtifact in ThisBuild := false

lazy val `druid4s-utils` = (project in file("utils"))
  .settings(libraryDependencies ++= jodaTime :+ scalatest)
  .settings(bintraySettings("GlobalWebIndex", "druid4s"))

lazy val IntegrationConf = config("it") extend Test

lazy val `druid4s-client` = (project in file("client"))
  .settings(libraryDependencies ++= Seq(randagen, scalaHttp, loggingImplLogback % "test", scalatest) ++ jackson)
  .settings(bintraySettings("GlobalWebIndex", "druid4s"))
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationConf)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings)
  )
  .settings(
    envVars in Test := Map(
      "BROKER_HOST"  -> sys.env.getOrElse("BROKER_HOST", "localhost"),
      "OVERLORD_HOST"  -> sys.env.getOrElse("OVERLORD_HOST", "localhost"),
      "COORDINATOR_HOST"  -> sys.env.getOrElse("COORDINATOR_HOST", "localhost")
    )
  )
  .dependsOn(`druid4s-utils` % "compile->compile;test->test")