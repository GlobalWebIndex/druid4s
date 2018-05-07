
version in ThisBuild := "0.2.5"
crossScalaVersions in ThisBuild := Seq("2.12.4", "2.11.8")
organization in ThisBuild := "net.globalwebindex"

lazy val randagenVersion = "0.1.4"

lazy val druid4s = (project in file("."))
  .settings(aggregate in update := false)
  .settings(publish := {})
  .aggregate(`Druid4s-client`, `Druid4s-utils`)

lazy val `Druid4s-utils` = (project in file("utils"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++= jodaTime ++ Seq(scalatest))
  .settings(publishSettings("GlobalWebIndex", "druid4s-utils", s3Resolver))

lazy val `Druid4s-client` = (project in file("client"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++=
    Seq(
      "net.globalwebindex" %% "randagen-core" % randagenVersion % "test",
      scalaHttp,
      loggingImplLog4j % "test",
      scalatest
    ) ++ loggingApi ++ jackson
  ).settings(publishSettings("GlobalWebIndex", "druid4s-client", s3Resolver))
    .dependsOn(`Druid4s-utils` % "compile->compile;test->test")