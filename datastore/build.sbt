import Dependencies._
import org.allenai.sbt.core.CoreDependencies._

name := "common-datastore"

libraryDependencies ++= Seq(
  slf4j,
  awsJavaSdk,
  commonsIO,
  allenAiCommon,
  allenAiTestkit % "test,it")
