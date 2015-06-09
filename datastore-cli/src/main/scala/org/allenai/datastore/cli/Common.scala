package org.allenai.datastore.cli

import org.allenai.datastore.Datastore

import scala.util.{ Try, Failure, Success }

object Common {
  def printDefaultDatastoreWarning() = {
    System.err.println("No datastore explicitly specified.")
    System.err.println("The default (public) datastore will be used.")
    System.err.println("To specify another datastore (such as 'private'), use `-d private`.")
  }

  def handleDatastoreExceptions[T](f: => T): Try[T] = {
    try {
      Success(f)
    } catch {
      case e: Datastore.Exception =>
        System.err.println("Error: " + e.getMessage)
        Failure(e)
    }
  }
}
