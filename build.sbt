import gwi.sbt.CommonPlugin
import gwi.sbt.CommonPlugin.autoImport._

crossScalaVersions in ThisBuild := Seq("2.11.8", "2.12.1")
organization in ThisBuild := "net.globalwebindex"

lazy val druid4s = (project in file("."))
  .aggregate(client)

lazy val client = (project in file("client"))
  .enablePlugins(CommonPlugin)
  .settings(name := "druid4s")
  .settings(libraryDependencies ++= jodaTime ++ loggingApi ++ testingDeps ++ jackson ++ Seq(scalaHttp, loggingImplLogback % "provided"))
  .settings(publishSettings("GlobalWebIndex", "druid4s", s3Resolver))
  .dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/randagen.git#v0.0.1"), "core") % "compile->compile;test->test")