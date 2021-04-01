import Dependencies._

import sbt._
import Keys._
import codeartifact.CodeArtifactKeys

object GlobalPlugin extends AutoPlugin {
  override def trigger = allRequirements

  override def projectConfigurations: Seq[Configuration] = Seq(IntegrationTest)

  override def projectSettings =
    Defaults.itSettings ++ inConfig(IntegrationTest)(
      org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings
    ) ++ Seq(
      organization := "org.allenai.datastore",
      scalaVersion := ScalaVersions.SCALA_213,
      CodeArtifactKeys.codeArtifactUrl := "https://org-allenai-s2-896129387501.d.codeartifact.us-west-2.amazonaws.com/maven/private",
      fork in Test := true,
      dependencyOverrides ++= Logging.loggingDependencyOverrides
    )
}
