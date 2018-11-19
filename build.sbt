import Dependencies._
import Deploy._

crossScalaVersions in ThisBuild := Seq("2.12.6", "2.11.8")
organization in ThisBuild := "net.globalwebindex"
libraryDependencies in ThisBuild ++= loggingApi

lazy val s3Resolver = "S3 Snapshots" at "s3://public.maven.globalwebindex.net.s3-eu-west-1.amazonaws.com/snapshots"
resolvers in ThisBuild ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/",
  s3Resolver
)
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))
cancelable in ThisBuild := true
publish := { }

lazy val `druid4s-utils` = (project in file("utils"))
  .settings(libraryDependencies ++= jodaTime :+ scalatest)
  .settings(publishSettings("GlobalWebIndex", "druid4s-utils", s3Resolver))

lazy val `druid4s-client` = (project in file("client"))
  .settings(libraryDependencies ++= Seq(randagen, scalaHttp, loggingImplLogback % "test", scalatest) ++ jackson)
  .settings(publishSettings("GlobalWebIndex", "druid4s-client", s3Resolver))
  .dependsOn(`druid4s-utils` % "compile->compile;test->test")