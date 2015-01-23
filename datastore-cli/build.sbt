import Dependencies._

import AssemblyKeys._

name := "datastore-cli"

libraryDependencies += scopt

addLoggingDependencies(libraryDependencies)

assemblySettings

jarName in assembly := "DatastoreCli.jar"

mainClass in assembly := Some("org.allenai.datastore.cli.DatastoreCli")
