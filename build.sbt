
version in ThisBuild := "0.0.4"
crossScalaVersions in ThisBuild := Seq("2.12.3", "2.11.8")
organization in ThisBuild := "net.globalwebindex"

lazy val randagenVersion = "0.0.5"

lazy val druid4s = (project in file("."))
  .aggregate(`druid4s-client`)

lazy val `druid4s-client` = (project in file("client"))
  .enablePlugins(CommonPlugin)
  .settings(name := "druid4s-client")
  .settings(libraryDependencies ++= jodaTime ++ loggingApi ++ jackson ++ Seq(scalaHttp, loggingImplLog4j % "provided", scalatest))
  .settings(publishSettings("GlobalWebIndex", "druid4s-client", s3Resolver))
  .dependsOn(ProjectRef(uri(s"https://github.com/GlobalWebIndex/randagen.git#v$randagenVersion"), "randagen-core") % "compile->compile;test->test")