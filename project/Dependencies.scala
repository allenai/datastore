import sbt._

import org.allenai.plugins.CoreDependencies

/** Object holding the dependencies Common has, plus resolvers and overrides. */
object Dependencies extends CoreDependencies {
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  val scalaTest = "org.scalatest" %% "scalatest" % "2.2.0"
  val scalaReflection = "org.scala-lang" % "scala-reflect" % "2.11.5"
  val pegdown = "org.pegdown" % "pegdown" % "1.4.2"
  val awsJavaSdk = "com.amazonaws" % "aws-java-sdk-s3" % "1.10.29"
  val commonsIO = "commons-io" % "commons-io" % "2.4"

  // Bridge jcl to slf4j (needed to bridge logging from awsJavaSdk to slf4j)
  val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % "1.7.7"
}
