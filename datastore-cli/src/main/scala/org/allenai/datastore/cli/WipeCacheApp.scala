package org.allenai.datastore.cli

import org.allenai.datastore.Datastore

object WipeCacheApp extends App {
  case class Options(datastore: Option[Datastore] = None)

  val parser = new scopt.OptionParser[Options]("scopt") {
    opt[String]('d', "datastore") action { (d, c) =>
      c.copy(datastore = Some(Datastore(d)))
    } text (s"Datastore to use. Default is ${Datastore.defaultName}")
  }

  Common.handleDatastoreExceptions {
    parser.parse(args, Options()) foreach { config =>
      val datastore = config.datastore.getOrElse {
        Common.printDefaultDatastoreWarning()
        Datastore
      }
      datastore.wipeCache()
    }
  }
}
