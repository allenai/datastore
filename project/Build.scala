import org.allenai.plugins._
import sbtrelease.ReleasePlugin._

import sbt._
import Keys._

object DatastoreBuild extends Build {
  val buildSettings = Seq(
    organization := "org.allenai",
    crossScalaVersions := Seq("2.11.4"),
    scalaVersion <<= crossScalaVersions { (vs: Seq[String]) => vs.head },
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-unchecked", "-feature"),
    conflictManager := ConflictManager.strict,
    dependencyOverrides ++= Dependencies.Overrides,
    resolvers ++= Dependencies.Resolvers,
    licenses := Seq(
      "Apache 2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))) ++ 
    CoreRepositories.PublishTo.ai2Private ++
    releaseSettings

  lazy val datastore = Project(
    id = "datastore",
    base = file("datastore"),
    settings = buildSettings ++ Defaults.itSettings
  ).enablePlugins(ReleasePlugin).
    configs(IntegrationTest)

  lazy val datastoreCli = Project(
    id = "datastore-cli",
    base = file("datastore-cli"),
    settings = buildSettings
  ).dependsOn(datastore).
    enablePlugins(ReleasePlugin)

  lazy val datastoreRoot = Project(id = "datastoreRoot", base = file(".")).settings(
    // Don't publish a jar for the root project.
    publishTo := Some("dummy" at "nowhere"), publish := { }, publishLocal := { }
  ).aggregate(datastore, datastoreCli).enablePlugins(ReleasePlugin)
}
