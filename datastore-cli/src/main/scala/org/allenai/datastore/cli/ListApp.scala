package org.allenai.datastore.cli

import org.allenai.datastore.Datastore

object ListApp extends App {
  case class Config(
    datastore: Option[Datastore] = None,
    group: Option[String] = None
  )

  val parser = new scopt.OptionParser[Config]("scopt") {
    opt[String]('d', "datastore") action { (d, c) =>
      c.copy(datastore = Some(Datastore(d)))
    } text (s"Datastore to use. Default is ${Datastore.defaultName}")

    opt[String]('g', "group") action { (g, c) =>
      c.copy(group = Some(g))
    } text ("Group name of the objects to list")
  }

  Common.handleDatastoreExceptions {
    parser.parse(args, Config()) foreach { config =>
      val datastore = config.datastore match {
        case Some(datastore) => datastore
        case None =>
          Common.printDefaultDatastoreWarning()
          Datastore
      }
      config.group match {
        case None =>
          datastore.listGroups.foreach(println)
        case Some(group) =>
          datastore.listGroupContents(group).foreach { l =>
            val nameSuffix = if (l.directory) "/" else ""
            println(s"${l.name}$nameSuffix\t${l.version}")
          }
      }
    }
  }
}
