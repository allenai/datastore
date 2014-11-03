import org.allenai.sbt.release.AllenaiReleasePlugin
import org.allenai.sbt.core.CoreSettings
import sbtrelease.ReleasePlugin._

import sbt._
import Keys._

object CommonBuild extends Build {
  val buildSettings = Seq(
    organization := "org.allenai.datastore",
    crossScalaVersions := Seq("2.10.4"),
    scalaVersion <<= crossScalaVersions { (vs: Seq[String]) => vs.head },
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-unchecked", "-feature"),
    conflictManager := ConflictManager.strict,
    dependencyOverrides ++= Dependencies.Overrides,
    resolvers ++= Dependencies.Resolvers,
    licenses := Seq(
      "Apache 2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))) ++ 
    CoreSettings.publishToRepos.ai2.publicRepo ++
    releaseSettings

  lazy val datastore = Project(
    id = "datastore",
    base = file("datastore"),
    settings = buildSettings ++ Defaults.itSettings
  ).enablePlugins(AllenaiReleasePlugin).
    configs(IntegrationTest)

  lazy val datastoreCli = Project(
    id = "datastore-cli",
    base = file("datastore-cli"),
    settings = buildSettings
  ).dependsOn(datastore).
    enablePlugins(AllenaiReleasePlugin)

  lazy val datastoreRoot = Project(id = "datastoreRoot", base = file(".")).settings(
    // Don't publish a jar for the root project.
    publishTo := Some("dummy" at "nowhere"), publish := { }, publishLocal := { }
  ).aggregate(datastore, datastoreCli).enablePlugins(AllenaiReleasePlugin)
}
