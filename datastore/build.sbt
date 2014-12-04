import Dependencies._

name := "datastore"

libraryDependencies ++= Seq(
  jclOverSlf4j,
  slf4j,
  awsJavaSdk,
  commonsIO,
  allenAiCommon,
  allenAiTestkit % "test,it")

fork in IntegrationTest := true
