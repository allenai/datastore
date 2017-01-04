import Dependencies._

name := "datastore-cli"

libraryDependencies += scopt

addLoggingDependencies(libraryDependencies)

assemblyJarName in assembly := "DatastoreCli.jar"

mainClass in assembly := Some("org.allenai.datastore.cli.DatastoreCli")
