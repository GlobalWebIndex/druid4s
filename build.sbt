
version in ThisBuild := "0.0.3"
crossScalaVersions in ThisBuild := Seq("2.12.3", "2.11.8")
organization in ThisBuild := "net.globalwebindex"

lazy val druid4s = (project in file("."))
  .aggregate(`druid4s-client`)

lazy val `druid4s-client` = (project in file("client"))
  .enablePlugins(CommonPlugin)
  .settings(name := "druid4s-client")
  .settings(libraryDependencies ++= jodaTime ++ loggingApi ++ jackson ++ Seq(scalaHttp, loggingImplLog4j % "provided", scalatest))
  .settings(publishSettings("GlobalWebIndex", "druid4s-client", s3Resolver))
  .dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/randagen.git#v0.0.4"), "randagen-core") % "compile->compile;test->test")