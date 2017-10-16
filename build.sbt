
version in ThisBuild := "0.0.9"
crossScalaVersions in ThisBuild := Seq("2.12.3", "2.11.8")
organization in ThisBuild := "net.globalwebindex"

lazy val randagenVersion = "0.0.9"

lazy val druid4s = (project in file("."))
  .settings(aggregate in update := false)
  .settings(publish := {})
  .aggregate(`druid4s-client`)

lazy val `druid4s-client` = (project in file("client"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++=
    Seq(
      "net.globalwebindex" %% "randagen-core" % randagenVersion % "test",
      scalaHttp,
      loggingImplLog4j % "test",
      scalatest
    ) ++ jodaTime ++ loggingApi ++ jackson
  ).settings(publishSettings("GlobalWebIndex", "druid4s-client", s3Resolver))