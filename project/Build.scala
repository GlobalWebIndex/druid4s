import sbt.Keys._
import sbt._

object Build extends sbt.Build {

  val jacksonVersion = "2.8.3"

  val deps = Seq(
    "org.scalaj"                    %%  "scalaj-http"           % "2.3.0",
    "joda-time"                     %   "joda-time"             % "2.9.4",
    "org.joda"                      %   "joda-convert"          % "1.8",
    "com.fasterxml.jackson.module"  %%  "jackson-module-scala"  % jacksonVersion,
    "com.fasterxml.jackson.core"    %   "jackson-core"          % jacksonVersion,
    "com.fasterxml.jackson.core"    %   "jackson-annotations"   % jacksonVersion,
    "ch.qos.logback"                %   "logback-classic"       % "1.1.7"                 % "provided",
    "com.typesafe.scala-logging"    %%  "scala-logging"         % "3.4.0",
    "org.slf4j"                     %   "slf4j-api"             % "1.7.21",
    "net.globalwebindex"            %%  "randagen"              % "0.10-SNAPSHOT"         % "test",
    "org.scalatest"                 %%  "scalatest"             % "3.0.0"                 % "test"
  )

  val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo := Some("S3 Snapshots" at "s3://public.maven.globalwebindex.net.s3-website-eu-west-1.amazonaws.com/snapshots"),
    pomExtra :=
      <url>https://github.com/GlobalWebIndex/druid4s</url>
        <licenses>
          <license>
            <name>The MIT License (MIT)</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:GlobalWebIndex/druid4s.git</url>
          <connection>scm:git:git@github.com:GlobalWebIndex/druid4s.git</connection>
        </scm>
        <developers>
          <developer>
            <id>l15k4</id>
            <name>Jakub Liska</name>
            <email>jakub@globalwebindex.net</email>
          </developer>
        </developers>
  )

  val testSettings = Seq(
    testOptions in Test += Tests.Argument("-oDF"),
    parallelExecution in Test := false,
    parallelExecution in ThisBuild := false,
    parallelExecution in IntegrationTest := false,
    testForkedParallel in ThisBuild := false,
    testForkedParallel in IntegrationTest := false,
    testForkedParallel in Test := false,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)
  )

  val sharedSettings = Seq(
    organization := "net.globalwebindex",
    version := "0.1.2-SNAPSHOT",
    scalaVersion := "2.11.8",
    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings",
      "-Xfuture",
      "-Yinline-warnings", "-Ywarn-adapted-args", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Yno-adapted-args"
    ),
    offline := true,
    resolvers ++= Seq(
      "S3 Snapshots" at "s3://public.maven.globalwebindex.net.s3-website-eu-west-1.amazonaws.com/snapshots",
      Resolver.sonatypeRepo("snapshots"),
      Resolver.typesafeRepo("releases"),
      Resolver.mavenLocal
    ),
    autoCompilerPlugins := true,
    cancelable in Global := true
  ) ++ testSettings

  lazy val client = (project in file("client"))
    .settings(name := "druid4s")
    .settings(sharedSettings)
    .settings(libraryDependencies ++= deps)
    .settings(publishSettings)
}