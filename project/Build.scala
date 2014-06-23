import sbt._
import Keys._

  val inheritedSettings = Defaults.defaultSettings ++ Format.settings ++ Publish.settings ++
    TravisPublisher.settings

  val buildSettings = inheritedSettings ++ Seq(
    organization := "org.allenai.common",
    crossScalaVersions := Seq("2.10.4"),
    scalaVersion <<= crossScalaVersions { (vs: Seq[String]) => vs.head },
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-unchecked", "-feature"),
    conflictManager := ConflictManager.strict,
    dependencyOverrides ++= Dependencies.Overrides,
    resolvers ++= Dependencies.Resolvers
  )

  lazy val testkit = Project(
    id = "testkit",
    base = file("testkit"),
    settings = buildSettings)

  lazy val common = Project(
    id = "core",
    base = file("core"),
    settings = buildSettings
  ).dependsOn(testkit % "test->compile")

  lazy val webapp = Project(
    id = "webapp",
    base = file("webapp"),
    settings = buildSettings
  ).dependsOn(common)

  lazy val root = Project(id = "root", base = file(".")).settings(
    // Don't publish a jar for the root project.
    publish := { }, publishLocal := { }
  ).aggregate(webapp, common, testkit)
}