import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

object Release {
  val noPublishSettings: Seq[Def.Setting[_]] = Seq(
    crossScalaVersions := Nil,
    releaseProcess := Nil,
    publish / skip := true
  )
  val publishSettings: Seq[Def.Setting[_]] = Seq(
    crossScalaVersions := ScalaVersions.SUPPORTED_SCALA_VERSIONS,
    publishArtifact in Test := false,
    releaseProcess := releaseSteps,
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("https://github.com/allenai/datastore")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/allenai/datastore"),
        "https://github.com/allenai/datastore.git"
      )
    )
  )

  val releaseSteps: Seq[ReleaseStep] = Seq(
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    // releaseStepCommandAndRemaining("+test"),
    // releaseStepCommandAndRemaining("+it:test"),
    setReleaseVersion,
    // commitReleaseVersion,
    // tagRelease,
    releaseStepCommandAndRemaining("+codeArtifactPublish")
    // setNextVersion,
    // commitNextVersion,
    // pushChanges
  )
}
