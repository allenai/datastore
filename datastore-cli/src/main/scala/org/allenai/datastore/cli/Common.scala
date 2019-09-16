package org.allenai.datastore.cli

import org.allenai.datastore.Datastore

import scala.util.{ Failure, Success, Try }

object Common {
  def printDefaultDatastoreWarning() = {
    System.err.println("No datastore explicitly specified.")
    System.err.println("The default (public) datastore will be used.")
    System.err.println("To specify another datastore (such as 'private'), use `-d private`.")
  }

  /** Catches datastore exceptions and converts them to an error message. All other exceptions
    * bubble up as normal.
    */
  def handleDatastoreExceptions(f: => Unit): Unit = {
    try {
      f
    } catch {
      case e: Datastore.DsException => System.err.println("Error: " + e.getMessage)
    }
  }
}
