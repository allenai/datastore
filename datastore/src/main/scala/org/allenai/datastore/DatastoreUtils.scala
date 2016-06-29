package org.allenai.datastore

import org.allenai.common.Logging

import java.io.File

import scala.io.{ BufferedSource, Codec, Source }

/** Various convenient utiltiies for accessing the Datastore. */
object DatastoreUtils extends Logging {

  /** Get a datastore file as a buffered Source. Caller is responsible for closing this stream. */
  def getDatastoreFileAsSource(
    datastoreName: String,
    group: String,
    name: String,
    version: Int
  )(implicit codec: Codec): BufferedSource = {
    logger.debug(s"Loading file from $datastoreName datastore: $group/$name-v$version")
    val file = Datastore(datastoreName).filePath(group, name, version).toFile
    Source.fromFile(file)(codec)
  }

  /** Get a datastore file as a buffered Source. Caller is responsible for closing this stream. */
  def getDatastoreFileAsSource(datastoreUriString: String): BufferedSource = {
    val (datastoreName, group, name, version) = parseDatastoreUri(datastoreUriString)
    getDatastoreFileAsSource(datastoreName, group, name, version)
  }

  /** Get a datastore directory as a folder. */
  def getDatastoreDirectoryAsFolder(
    datastoreName: String,
    group: String,
    name: String,
    version: Int
  ): File = {
    logger.debug(s"Loading directory from $datastoreName datastore: $group/$name-v$version")
    Datastore(datastoreName).directoryPath(group, name, version).toFile
  }

  /** Get a datastore directory as a folder */
  def getDatastoreDirectoryAsFolder(datastoreUriString: String): File = {
    val (datastoreName, group, name, version) = parseDatastoreUri(datastoreUriString)
    getDatastoreDirectoryAsFolder(datastoreName, group, name, version)
  }

  /** Regex to use for parsing Datastore URIs in parseDatastoreUri() */
  private val datastoreUriRegex = """datastore://([^/]+)/([^/]+)/(.+)-v(\d+)(\..*)?""".r

  /** Parse datastore URIs such as the following to produce datastore name, group, file/folder name,
    * and version:
    * datastore://private/org.allenai.aristo.tables/Grade4-v10.json  (with extension)
    * datastore://private/org.allenai.aristo.tabledata/tables-v4  (without extension)
    */
  def parseDatastoreUri(datastoreUriString: String): (String, String, String, Int) = {
    datastoreUriString match {
      case datastoreUriRegex(datastoreName, group, basename, version, extension) =>
        val ext = if (extension == null) "" else extension // extension is optional
        (datastoreName, group, basename + ext, version.toInt)
      case _ => throw new IllegalArgumentException(s"Cannot parse $datastoreUriString")
    }
  }
}
