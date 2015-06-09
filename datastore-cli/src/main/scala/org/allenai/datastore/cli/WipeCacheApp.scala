package org.allenai.datastore.cli

import org.allenai.datastore.Datastore

object WipeCacheApp extends App {
  case class Config(datastore: Option[Datastore] = None)

  val parser = new scopt.OptionParser[Config]("scopt") {
    opt[String]('d', "datastore") action { (d, c) =>
      c.copy(datastore = Some(Datastore(d)))
    } text (s"Datastore to use. Default is ${Datastore.defaultName}")
  }

  Common.handleDatastoreExceptions {
    parser.parse(args, Config()) foreach { config =>
      val datastore = config.datastore match {
        case Some(datastore) => datastore
        case None =>
          Common.printDefaultDatastoreWarning()
          Datastore
      }
      datastore.wipeCache()
    }
  }
}
