package org.allenai.datastore.cli

import org.allenai.datastore.Datastore

object UrlApp extends App {
  case class Options(
    assumeFile: Boolean = false,
    assumeDirectory: Boolean = false,
    group: String = null,
    name: String = null,
    version: Int = -1,
    datastore: Option[Datastore] = None
  )

  val parser = new scopt.OptionParser[Options]("scopt") {
    opt[Boolean]("assumeFile").action { (f, c) =>
      c.copy(assumeFile = f)
    } text ("Assumes that the object in the datastore is a file.")

    opt[Boolean]("assumeDirectory").action { (d, c) =>
      c.copy(assumeDirectory = d)
    } text ("Assumes that the object in the datastore is a directory.")

    checkConfig { c =>
      if (c.assumeDirectory && c.assumeFile) {
        failure("You can't specify both assumeDirectory and assumeFile")
      } else {
        success
      }
    }

    note(
      "If you specify neither assumeDirectory nor assumeFile, the tool will autodetect " +
        "whether the object in the datastore is a file or a directory."
    )

    opt[String]('g', "group").required().action { (g, c) =>
      c.copy(group = g)
    } text ("Group name of the object in the datastore")

    opt[String]('n', "name").required().action { (n, c) =>
      c.copy(name = n)
    } text ("Name of the object in the datastore")

    opt[Int]('v', "version").required().action { (v, c) =>
      c.copy(version = v)
    } text ("Version number of the object in the datastore")

    opt[String]('d', "datastore").action { (d, c) =>
      c.copy(datastore = Some(Datastore(d)))
    } text (s"Datastore to use. Default is ${Datastore.defaultName}")

    help("help")
  }

  Common.handleDatastoreExceptions {
    parser.parse(args, Options()).foreach { config =>
      val datastore = config.datastore.getOrElse {
        Common.printDefaultDatastoreWarning()
        Datastore
      }
      val directory = if (config.assumeDirectory) {
        true
      } else if (config.assumeFile) {
        false
      } else {
        datastore.exists(datastore.Locator(config.group, config.name, config.version, true))
      }

      val locator = datastore.Locator(config.group, config.name, config.version, directory)
      println(datastore.url(locator))
    }
  }
}
