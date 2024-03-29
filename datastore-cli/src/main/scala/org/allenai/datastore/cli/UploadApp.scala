package org.allenai.datastore.cli

import org.allenai.datastore.Datastore

import java.io.File

object UploadApp extends App {
  case class Options(
    path: File = null,
    group: String = null,
    name: String = null,
    version: Int = -1,
    datastore: Option[Datastore] = None,
    overwrite: Boolean = false
  )

  val parser = new scopt.OptionParser[Options]("scopt") {
    opt[File]('p', "path").required().action { (p, c) =>
      c.copy(path = p)
    } text ("Path to the file or directory you want uploaded")

    opt[String]('g', "group").required().action { (g, c) =>
      c.copy(group = g)
    } text ("Group name to store the file or directory under")

    opt[String]('n', "name").required().action { (n, c) =>
      c.copy(name = n)
    } text ("Name to store the file or directory under")

    opt[Int]('v', "version").required().action { (v, c) =>
      c.copy(version = v)
    } text ("Version number to store the file or directory under")

    opt[String]('d', "datastore").action { (d, c) =>
      c.copy(datastore = Some(Datastore(d)))
    } text (s"Datastore to use. Default is ${Datastore.defaultName}")

    opt[Boolean]("overwrite").action { (_, c) =>
      c.copy(overwrite = true)
    } text ("Overwrite if the group, version, and name already exists")

    help("help")
  }

  Common.handleDatastoreExceptions {
    parser.parse(args, Options()).foreach { config =>
      val datastore = config.datastore.getOrElse {
        Common.printDefaultDatastoreWarning()
        Datastore
      }

      val locator = datastore.Locator(
        config.group,
        config.name,
        config.version,
        config.path.isDirectory
      )
      datastore.publish(config.path.toPath, locator, config.overwrite)
    }
  }
}
