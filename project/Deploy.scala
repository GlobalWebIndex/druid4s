import sbt.Keys._
import sbt._

object Deploy {
  def settings(ghOrganizationName: String, ghProjectName: String) = Seq(
    publishArtifact := true,
    publishMavenStyle := true,
    publishArtifact in Test := false,
    homepage := Some(url(
      s"https://github.com/$ghOrganizationName/$ghProjectName/blob/master/README.md")),
    licenses in ThisBuild += ("MIT", url("http://opensource.org/licenses/MIT")),
    developers += Developer("l15k4",
                            "Jakub Liska",
                            "liska.jakub@gmail.com",
                            url("https://github.com/l15k4")),
    scmInfo := Some(
      ScmInfo(url(s"https://github.com/$ghOrganizationName/$ghProjectName"),
              s"git@github.com:$ghOrganizationName/$ghProjectName.git")),
    pomIncludeRepository := { _ =>
      false
    },
    publishTo := Some("GitHub Package Registry" at s"https://maven.pkg.github.com/$ghOrganizationName/$ghProjectName"),
    credentials ++= sys.env.get("DMP_TEAM_GITHUB_TOKEN").map(Credentials("GitHub Package Registry", "maven.pkg.github.com", "dmp-team", _)),
    pomExtra :=
      <url>https://github.com/{ghOrganizationName}/{ghProjectName}</url>
        <licenses>
          <license>
            <name>The MIT License (MIT)</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:{ghOrganizationName}/{ghProjectName}.git</url>
          <connection>scm:git:git@github.com:{ghOrganizationName}/{ghProjectName}.git</connection>
        </scm>
        <developers>
          <developer>
            <id>l15k4</id>
            <name>Jakub Liska</name>
            <email>liska.jakub@gmail.com</email>
          </developer>
        </developers>
  )
}
