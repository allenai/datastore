import Dependencies._

name := "datastore"

libraryDependencies ++= Seq(
  awsJavaSdk,
  commonsIO,
  allenAiCommon,
  allenAiTestkit % "test,it")

addLoggingDependencies(libraryDependencies)

fork in IntegrationTest := true
