package org.allenai.datastore

import org.allenai.common.Logging

import java.io.File
import java.nio.file.Path

import scala.io.{ BufferedSource, Codec, Source }

/** Various convenient utilities for accessing the Datastore. */
object DatastoreUtils extends Logging {

  /** Get a Datastore file path. */
  def getDatastoreFilePath(
    datastoreName: String,
    group: String,
    name: String,
    version: Int
  ): Path = Datastore(datastoreName).filePath(group, name, version)

  /** Get a Datastore file path, given a URI. */
  def getDatastoreFilePath(datastoreUri: String): Path = {
    val (datastoreName, group, name, version) = parseDatastoreUri(datastoreUri)
    getDatastoreFilePath(datastoreName, group, name, version)
  }

  /** Get a Datastore file as a buffered Source. Caller is responsible for closing this stream. */
  def getDatastoreFileAsSource(
    datastoreName: String,
    group: String,
    name: String,
    version: Int
  )(implicit codec: Codec): BufferedSource = {
    logger.debug(s"Loading file from $datastoreName datastore: $group/$name-v$version")
    val file = getDatastoreFilePath(datastoreName, group, name, version).toFile
    Source.fromFile(file)(codec)
  }

  /** Get a Datastore file as a buffered Source. Caller is responsible for closing this stream. */
  def getDatastoreFileAsSource(datastoreUri: String)(implicit codec: Codec): BufferedSource = {
    val (datastoreName, group, name, version) = parseDatastoreUri(datastoreUri)
    getDatastoreFileAsSource(datastoreName, group, name, version)(codec)
  }

  /** Get a Datastore directory path. */
  def getDatastoreDirectoryPath(
    datastoreName: String,
    group: String,
    name: String,
    version: Int
  ): Path = Datastore(datastoreName).directoryPath(group, name, version)

  /** Get a Datastore directory path, given a URI. */
  def getDatastoreDirectoryPath(datastoreUri: String): Path = {
    val (datastoreName, group, name, version) = parseDatastoreUri(datastoreUri)
    getDatastoreDirectoryPath(datastoreName, group, name, version)
  }

  /** Get a Datastore directory as a folder. */
  def getDatastoreDirectoryAsFolder(
    datastoreName: String,
    group: String,
    name: String,
    version: Int
  ): File = {
    logger.debug(s"Loading directory from $datastoreName datastore: $group/$name-v$version")
    getDatastoreDirectoryPath(datastoreName, group, name, version).toFile
  }

  /** Get a Datastore directory as a folder, given a URI. */
  def getDatastoreDirectoryAsFolder(datastoreUri: String): File = {
    val (datastoreName, group, name, version) = parseDatastoreUri(datastoreUri)
    getDatastoreDirectoryAsFolder(datastoreName, group, name, version)
  }

  /** Regex to use for parsing Datastore URIs in parseDatastoreUri() */
  private val datastoreUriRegex = """datastore://([^/]+)/([^/]+)/(.+)-v(\d+)(\..*)?""".r

  /** Parse datastore URIs such as the following to produce datastore name, group, file/folder name,
    * and version:
    * datastore://private/org.allenai.aristo.tables/Grade4-v10.json  (with extension)
    * datastore://private/org.allenai.aristo.tabledata/tables-v4  (without extension)
    */
  def parseDatastoreUri(datastoreUri: String): (String, String, String, Int) = {
    datastoreUri match {
      case datastoreUriRegex(datastoreName, group, basename, version, extension) =>
        val ext = if (extension == null) "" else extension // extension is optional
        (datastoreName, group, basename + ext, version.toInt)
      case _ => throw new IllegalArgumentException(s"Cannot parse $datastoreUri")
    }
  }
}
