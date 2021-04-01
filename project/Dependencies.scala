import sbt._

object Dependencies {
  val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.3"

  val commonVersion = "2.3.1"
  val allenAiCommon = "org.allenai.common" %% "common-core" % commonVersion
  val allenAiTestkit = "org.allenai.common" %% "common-testkit" % commonVersion

  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8"
  //val scalaReflection = "org.scala-lang" % "scala-reflect" % "2.11.8"
  val pegdown = "org.pegdown" % "pegdown" % "1.4.2"
  val awsJavaSdk = "com.amazonaws" % "aws-java-sdk-s3" % "1.10.29"
  val commonsIO = "commons-io" % "commons-io" % "2.4"

  // Bridge jcl to slf4j (needed to bridge logging from awsJavaSdk to slf4j)
  val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % "1.7.7"

  val scopt = "com.github.scopt" %% "scopt" % "3.7.1"

  object Logging {
    val slf4jVersion = "1.7.28"
    val logbackVersion = "1.2.3"
    // The logging API to use. This should be the only logging dependency of any API artifact
    // (anything that's going to be depended on outside of this SBT project).
    val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion
    val logbackCore = "ch.qos.logback" % "logback-core" % logbackVersion
    val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVersion

    val loggingDependencyOverrides = Seq(
      Logging.slf4jApi,
      Logging.logbackCore,
      Logging.logbackClassic
    )
  }

  // Copied from sbt-plugins :/ (TODO: remove this ...)
  // slf4j implementation (logback), and the log4j -> slf4j bridge.
  // This should be called on libraryDependencies like:
  // addLoggingDependencies(libraryDependencies)
  // TODO(markschaake&jkinkead): more comments about what is going on here
  def addLoggingDependencies(deps: SettingKey[Seq[ModuleID]]): Seq[Setting[Seq[ModuleID]]] = {
    import Logging._
    val cleanedDeps = deps ~= { seq =>
      seq map { module =>
        // Exclude the transitive dependencies that might mess things up for us.
        // slf4j replaces log4j.
        (module
          exclude ("log4j", "log4j")
          exclude ("commons-logging", "commons-logging")
        // We're using logback as the slf4j implementation, and we're providing it below.
          exclude ("org.slf4j", "slf4j-log4j12")
          exclude ("org.slf4j", "slf4j-jdk14")
          exclude ("org.slf4j", "slf4j-jcl")
          exclude ("org.slf4j", "slf4j-simple")
        // We'll explicitly provide the logback version; this avoids having to do an override.
          exclude ("ch.qos.logback", "logback-core")
          exclude ("ch.qos.logback", "logback-classic")
        // We add bridges explicitly as well
          exclude ("org.slf4j", "log4j-over-slf4j")
          exclude ("org.slf4j", "jcl-over-slf4j"))
      }
    }
    // Now, add the logging libraries.
    val logbackDeps = deps ++= Seq(
      slf4jApi,
      // Bridge log4j logging to slf4j.
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion,
      // Bridge jcl logging to slf4j.
      "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
      // Use logback for the implementation.
      logbackCore,
      logbackClassic
    )
    Seq(cleanedDeps, logbackDeps)
  }

}
