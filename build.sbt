lazy val buildSettings = Seq(
  organization := "org.allenai",
  crossScalaVersions := Seq("2.11.5"),
  scalaVersion <<= crossScalaVersions { (vs: Seq[String]) => vs.head },
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  licenses := Seq("Apache 2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/allenai/datastore")),
  scmInfo := Some(ScmInfo(
    url("https://github.com/allenai/common"),
    "https://github.com/allenai/common.git")),
  ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value,
  pomExtra := (
    <developers>
      <developer>
        <id>allenai-dev-role</id>
        <name>Paul Allen Institute for Artificial Intelligence</name>
        <email>dev-role@allenai.org</email>
      </developer>
    </developers>),
  dependencyOverrides += "com.typesafe" % "config" % "1.2.1") ++
  PublishTo.sonatype

lazy val datastore = Project(
  id = "datastore",
  base = file("datastore"),
  settings = buildSettings ++ Defaults.itSettings
).enablePlugins(LibraryPlugin).
  configs(IntegrationTest)

lazy val datastoreCli = Project(
  id = "datastore-cli",
  base = file("datastore-cli"),
  settings = buildSettings
).dependsOn(datastore).
  enablePlugins(LibraryPlugin)

lazy val datastoreRoot = Project(id = "datastoreRoot", base = file(".")).settings(
  // Don't publish a jar for the root project.
  publishArtifact := false,
  publishTo := Some("dummy" at "nowhere"),
  publish := { },
  publishLocal := { }
).aggregate(datastore, datastoreCli).enablePlugins(LibraryPlugin)
