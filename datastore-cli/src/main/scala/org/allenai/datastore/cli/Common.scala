package org.allenai.datastore.cli

object Common {
  def printDefaultDatastoreWarning() = {
    System.err.println("No datastore explicitly specified.")
    System.err.println("The default (public) datastore will be used.")
    System.err.println("To specify another datastore (such as 'private'), use `-d private`.")
  }
}
