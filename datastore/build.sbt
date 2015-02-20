import Dependencies._

name := "datastore"

libraryDependencies ++= Seq(
  awsJavaSdk exclude("commons-logging", "commons-logging"),
  commonsIO,
  allenAiCommon,
  allenAiTestkit % "test,it",
  Logging.logbackClassic,
  Logging.logbackCore,
  Logging.slf4jApi,
  "org.slf4j" % "jcl-over-slf4j" % Logging.slf4jVersion)

fork in IntegrationTest := true
