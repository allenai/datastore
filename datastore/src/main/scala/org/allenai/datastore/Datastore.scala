package org.allenai.datastore

import java.io.InputStream
import java.net.{ URI, URL }
import java.nio.ByteBuffer
import java.nio.channels.{ Channels, ReadableByteChannel, WritableByteChannel }
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.{ ZipEntry, ZipFile, ZipOutputStream }

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.event.{ ProgressEvent, ProgressListener }
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.transfer.TransferManager
import org.allenai.common.{ Logging, Resource }
import org.apache.commons.io.FileUtils
import org.slf4j.{ Logger, LoggerFactory }
import ch.qos.logback.classic.Level

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{ Success, Failure, Try, Random }

/** Represents a datastore
  *
  * This is a thin layer over an S3 bucket that stores the data. Data is identified by group
  * ("org.allenai.something"), name ("WordNet"), and version (an integer). It supports files as well
  * as directories.
  *
  * Items are published to the datastore, and then referred to with the *path() methods. All data is
  * cached, so access to all items should be very fast, except for the first time.
  *
  * It might make more sense to get Datastore objects from the companion object, rather than
  * creating them here.
  *
  * @param name name of the datastore. Corresponds to the name of the bucket in S3. Currently we
  *       have "public" and "private".
  * @param s3   properly authenticated S3 client.
  */
class Datastore(val name: String, val s3: AmazonS3Client) extends Logging {

  private val random = new Random
  @tailrec
  private def withRetries[T](activity: String, retries: Int = 10)(f: => T): T =
    if (retries <= 0) {
      f
    } else {
      val sleepTime = random.nextInt(10000) + 10000 // sleep between 10 and 20 seconds
      // If something goes wrong, we sleep a random amount of time, to make sure that we don't slam
      // the server, get timeouts, wait for exactly the same amount of time on all threads, and then
      // slam the server again.

      try {
        f
      } catch {
        case NonFatal(e) if !e.isInstanceOf[AccessDeniedException] && !e.isInstanceOf[DoesNotExistException] =>
          logger.warn(s"$e while $activity. $retries retries left.")
          Thread.sleep(sleepTime)
          withRetries(activity, retries - 1)(f)
      }
    }

  private val baseCacheDir = {
    val defaultCacheDir = if (System.getProperty("os.name").contains("Mac OS X")) {
      Paths.get(System.getProperty("user.home")).
        resolve("Library").
        resolve("Caches").
        resolve("org.allenai.datastore")
    } else {
      Paths.get(System.getProperty("user.home")).
        resolve(".ai2").
        resolve("datastore")
    }

    val envCacheDir = System.getenv("AI2_DATASTORE_DIR")
    val propCacheDir = System.getProperty("org.allenai.datastore.dir")

    Seq(envCacheDir, propCacheDir).
      filter(_ != null).
      map(Paths.get(_)).
      headOption.getOrElse(defaultCacheDir)
  }

  private val cacheDir = baseCacheDir.resolve(name)
  private val tempDir = baseCacheDir.resolve("tmp")
  // tempDir must be on the same filesystem as the cache itself, so that's why we put it here
  Files.createDirectories(tempDir)

  /** Returns the name of the bucket backing this datastore
    */
  def bucketName: String = s"$name.store.dev.allenai.org"

  /** Identifies a single version of a file or directory in the datastore */
  case class Locator(group: String, name: String, version: Int, directory: Boolean) {
    require(!group.contains("/"))
    require(!name.contains("/"))
    require(version > 0)

    private[Datastore] def nameWithVersion: String = {
      if (directory) {
        s"$name-d$version.zip"
      } else {
        val lastDotIndex = name.lastIndexOf('.')
        if (lastDotIndex < 0) {
          s"$name-v$version"
        } else {
          name.substring(0, lastDotIndex) + s"-v$version" + name.substring(lastDotIndex)
        }
      }
    }
    private[Datastore] def s3key: String = s"$group/$nameWithVersion"
    private[Datastore] def localCacheKey: String =
      if (directory) s3key.stripSuffix(".zip") else s3key
    private[Datastore] def flatLocalCacheKey: String = localCacheKey.replace('/', '%')
    private[Datastore] def localCachePath: Path = cacheDir.resolve(localCacheKey)
    private[Datastore] def lockfilePath: Path = cacheDir.resolve(localCacheKey + ".lock")

    def path = Datastore.this.path(this)
  }

  object Locator {
    implicit val defaultOrdering = new Ordering[Locator] {
      def compare(x: Locator, y: Locator): Int = {
        val ordering = implicitly[Ordering[(String, Int, Boolean)]]
        ordering.compare((x.name, x.version, x.directory), (y.name, y.version, y.directory))
      }
    }
    private[Datastore] def fromKey(key: String) = {
      val withExtension = """([^/]*)/(.*)-(.)(\d*)\.(.*)""".r
      val withoutExtension = """([^/]*)/(.*)-(.)(\d*)""".r

      // pattern matching on Int
      object Int {
        def unapply(s: String): Option[Int] = try {
          Some(s.toInt)
        } catch {
          case _: java.lang.NumberFormatException => None
        }
      }

      key match {
        case withExtension(group, name, "v", Int(version), ext) =>
          Locator(group, s"$name.$ext", version, false)
        case withoutExtension(group, name, "v", Int(version)) =>
          Locator(group, name, version, false)
        case withExtension(group, name, "d", Int(version), "zip") =>
          Locator(group, name, version, true)
        case _ =>
          throw new IllegalArgumentException(s"$key cannot be parsed as a datastore key")
      }
    }
  }

  /** Common base class for all datastore exceptions, so they can be caught together */
  class DsException(message: String, cause: Throwable) extends scala.Exception(message, cause)

  /** Exception indicating that we tried to access an item in the datastore that wasn't there.
    *
    * @param locator Locator of the object that wasn't there
    * @param cause   More detailed reason, or null
    */
  class DoesNotExistException(
    locator: Locator,
    cause: Throwable = null
  ) extends DsException(
    s"${locator.s3key} does not exist in the $name datastore",
    cause
  )

  /** Exception indicating that we tried to upload an item to the datastore that already exists.
    *
    * Data in the datastore is (mostly) immutable. Replacing an item is possible, but you have to
    * set a flag. If you don't set the flag, and you're replacing something, this exception gets
    * thrown.
    *
    * @param locator Locator of the object that's already there
    * @param cause   More detailed reason, or null
    */
  class AlreadyExistsException(
    locator: Locator,
    cause: Throwable = null
  ) extends DsException(
    s"${locator.s3key} already exists in the $name datastore",
    cause
  )

  /** Exception indicating that we tried to access a datastore that we don't have access to.
    *
    * @param cause More detailed reason, or null
    */
  class AccessDeniedException(cause: Throwable = null) extends DsException(
    s"You don't have access to the $name datastore. " +
      "Check https://github.com/allenai/wiki/wiki/" +
      "Getting-Started#setting-up-your-developer-environment " +
      "for information about configuring your system to get access.", cause
  )

  private def accessDeniedWrapper[T](f: => T): T = {
    try {
      f
    } catch {
      case e: AmazonS3Exception if e.getStatusCode == 403 =>
        throw new AccessDeniedException(e)
    }
  }

  /** Utility function for getting an InputStream for an object in S3
    *
    * @param key the key of the object
    * @return an InputStream with the contents of the object
    */
  private def getS3Object(key: String): InputStream = accessDeniedWrapper {
    s3.getObject(bucketName, key).getObjectContent
  }

  /** Waits until the given lockfile no longer exists
    *
    * @param lockfile path to the lockfile
    */
  private def waitForLockfile(lockfile: Path): Unit = {
    // TODO: Use watch interfaces instead of busy wait
    val start = System.currentTimeMillis()
    while (Files.exists(lockfile)) {
      val message = s"Waiting for lockfile at $lockfile"
      if (System.currentTimeMillis() - start > 60 * 1000) {
        logger.warn(message)
      } else {
        logger.info(message)
      }
      val oneSecond = 1000
      Thread.sleep(oneSecond)
    }
  }

  /** Tries to create an empty file.
    *
    * @param file path to the file to be created
    * @return true if the file was created, false if it already exists
    */
  private def tryCreateFile(file: Path): Boolean = {
    try {
      Files.createFile(file)
      true
    } catch {
      case _: FileAlreadyExistsException => false
    }
  }

  private def formatBytes(bytes: Long) = {
    val orderOfMagnitude =
      Math.floor(Math.log(Math.max(1, bytes)) / Math.log(1024))

    val bytesInUnit = bytes / Math.pow(1024, orderOfMagnitude)
    val formattedNumber = if (bytesInUnit < 10) {
      "%.2f" format bytesInUnit
    } else if (bytesInUnit < 100) {
      "%.1f" format bytesInUnit
    } else {
      "%.0f" format bytesInUnit
    }

    val units = Array("B", "KB", "MB", "GB", "TB", "PB")
    val unit = units(Math.min(orderOfMagnitude.toInt, units.length))
    s"$formattedNumber $unit"
  }

  private def copyStreams(
    ic: ReadableByteChannel,
    oc: WritableByteChannel,
    filename: String,
    silent: Boolean = false
  ): Unit = {
    val buffer = ByteBuffer.allocateDirect(1024 * 1024)

    val loggingDelay = 1000 // milliseconds
    val startTime = System.currentTimeMillis
    var lastLogMessage = startTime
    def shouldLog = !silent && System.currentTimeMillis - lastLogMessage >= loggingDelay
    var bytesCopied: Long = 0

    while (ic.read(buffer) >= 0) {
      bytesCopied += buffer.position
      buffer.flip()
      oc.write(buffer)
      buffer.compact()
      bytesCopied -= buffer.position

      if (shouldLog) {
        logger.info(
          s"Downloading $filename from the $name datastore. " +
            s"${formatBytes(bytesCopied)} bytes read."
        )
        lastLogMessage = System.currentTimeMillis
      }
    }

    buffer.flip()
    bytesCopied += buffer.remaining()
    while (buffer.hasRemaining)
      oc.write(buffer)

    if (!silent && System.currentTimeMillis - startTime >= loggingDelay) {
      logger.info(
        s"Downloaded $filename from the $name datastore. " +
          s"${formatBytes(bytesCopied)} bytes read."
      )
    }
  }

  //
  // Getting data out of the datastore
  //

  /** Gets a local path for a file in the datastore
    *
    * Downloads the file from S3 if necessary
    *
    * @param group   the group of the file
    * @param name    the name of the file
    * @param version the version of the file
    * @return path to the file on the local filesystem
    */
  def filePath(group: String, name: String, version: Int): Path =
    path(Locator(group, name, version, false))

  /** Gets a local path for a directory in the datastore
    *
    * Downloads the directory from S3 if necessary
    *
    * @param group   the group of the directory
    * @param name    the name of the directory
    * @param version the version of the directory
    * @return path to the directory on the local filesystem
    */
  def directoryPath(group: String, name: String, version: Int): Path =
    path(Locator(group, name, version, true))

  /** Gets a local path for an item in the datastore
    *
    * Downloads the item from S3 if necessary
    *
    * @param locator locator for the item in the datastore
    * @return path to the item on the local filesystem
    */
  def path(locator: Locator): Path = {
    Files.createDirectories(cacheDir)
    Files.createDirectories(locator.lockfilePath.getParent)
    waitForLockfile(locator.lockfilePath)

    if ((locator.directory && Files.isDirectory(locator.localCachePath)) ||
      (!locator.directory && Files.isRegularFile(locator.localCachePath))) {
      locator.localCachePath
    } else {
      val created = tryCreateFile(locator.lockfilePath)
      if (!created) {
        path(locator)
      } else {
        TempCleanup.remember(locator.lockfilePath)
        try {

          // We're downloading to a temp file first. If we were downloading into
          // the file directly, and we died half-way through the download, we'd
          // leave half a file, and that's not good.
          val tempFile =
            Files.createTempFile(tempDir, "ai2-datastore-" + locator.flatLocalCacheKey, ".tmp")
          TempCleanup.remember(tempFile)
          withRetries(s"downloading ${locator.s3key}") {
            try {
              Resource.using2(
                Channels.newChannel(getS3Object(locator.s3key)),
                Files.newByteChannel(
                  tempFile,
                  StandardOpenOption.WRITE,
                  StandardOpenOption.TRUNCATE_EXISTING
                )
              ) {
                  case (input, output) => copyStreams(input, output, locator.s3key)
                }
            } catch {
              case e: AmazonS3Exception if e.getErrorCode == "NoSuchKey" =>
                throw new DoesNotExistException(locator, e)
            }
          }

          if (locator.directory) {
            val tempZipDir =
              Files.createTempDirectory(tempDir, "ai2-datastore-" + locator.flatLocalCacheKey)
            TempCleanup.remember(tempZipDir)

            // download and extract the zip file to the directory
            Resource.using(new ZipFile(tempFile.toFile)) { zipFile =>
              val entries = zipFile.entries()
              while (entries.hasMoreElements) {
                val entry = entries.nextElement()
                if (entry.getName != "/") {
                  val pathForEntry = tempZipDir.resolve(entry.getName)
                  if (entry.isDirectory) {
                    Files.createDirectories(pathForEntry)
                  } else {
                    Files.createDirectories(pathForEntry.getParent)
                    Resource.using2(
                      Channels.newChannel(zipFile.getInputStream(entry)),
                      Files.newByteChannel(
                        pathForEntry,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                      )
                    ) {
                        case (input, output) => copyStreams(input, output, locator.s3key, true)
                      }
                  }
                }
              }
            }
            Files.delete(tempFile)
            TempCleanup.forget(tempFile)

            // move the directory where it belongs
            Files.move(tempZipDir, locator.localCachePath)
            TempCleanup.forget(tempZipDir)
          } else {
            Files.createDirectories(locator.localCachePath.getParent)
            Files.move(tempFile, locator.localCachePath)
            TempCleanup.forget(tempFile)
          }

          locator.localCachePath
        } finally {
          Files.delete(locator.lockfilePath)
          TempCleanup.forget(locator.lockfilePath)
        }
      }
    }
  }

  //
  // Putting data into the datastore
  //

  private def multipartUpload(path: Path, locator: Locator): Unit =
    withRetries(s"uploading to ${locator.s3key}") {
      accessDeniedWrapper {
        val tm = new TransferManager(s3)
        try {
          val request = new PutObjectRequest(bucketName, locator.s3key, path.toFile)
          request.setGeneralProgressListener(new ProgressListener {
            private var lastLogMessage = System.currentTimeMillis()

            override def progressChanged(progressEvent: ProgressEvent): Unit = {
              val now = System.currentTimeMillis()
              if (now - lastLogMessage >= 1000) {
                logger.info(
                  s"Uploading $path to the $name datastore. " +
                    s"${formatBytes(progressEvent.getBytesTransferred)} bytes written."
                )
                lastLogMessage = now
              }
            }
          })

          tm.upload(request).waitForCompletion()
        } finally {
          tm.shutdownNow(false)
        }
      }
    }

  /** Publishes a file to the datastore
    *
    * @param file      name of the file to be published
    * @param group     group to publish the file under
    * @param name      name to publish the file under
    * @param version   version to publish the file under
    * @param overwrite if true, overwrites possible existing items in the datastore
    */
  def publishFile(
    file: String,
    group: String,
    name: String,
    version: Int,
    overwrite: Boolean
  ): Unit =
    publishFile(Paths.get(file), group, name, version, overwrite)

  /** Publishes a file to the datastore
    *
    * @param file      path to the file to be published
    * @param group     group to publish the file under
    * @param name      name to publish the file under
    * @param version   version to publish the file under
    * @param overwrite if true, overwrites possible existing items in the datastore
    */
  def publishFile(
    file: Path,
    group: String,
    name: String,
    version: Int,
    overwrite: Boolean
  ): Unit =
    publish(file, Locator(group, name, version, false), overwrite)

  /** Publishes a directory to the datastore
    *
    * @param path      name of the directory to be published
    * @param group     group to publish the directory under
    * @param name      name to publish the directory under
    * @param version   version to publish the directory under
    * @param overwrite if true, overwrites possible existing items in the datastore
    */
  def publishDirectory(
    path: String,
    group: String,
    name: String,
    version: Int,
    overwrite: Boolean
  ): Unit =
    publishDirectory(Paths.get(path), group, name, version, overwrite)

  /** Publishes a directory to the datastore
    *
    * @param path      path to the directory to be published
    * @param group     group to publish the directory under
    * @param name      name to publish the directory under
    * @param version   version to publish the directory under
    * @param overwrite if true, overwrites possible existing items in the datastore
    */
  def publishDirectory(
    path: Path,
    group: String,
    name: String,
    version: Int,
    overwrite: Boolean
  ): Unit =
    publish(path, Locator(group, name, version, true), overwrite)

  /** Publishes an item to the datastore
    *
    * @param path      path to the item to be published
    * @param locator   locator to publish the item under
    * @param overwrite if true, overwrites possible existing items in the datastore
    */
  def publish(path: Path, locator: Locator, overwrite: Boolean): Unit = {
    if (!overwrite && exists(locator)) {
      throw new AlreadyExistsException(locator)
    }

    if (locator.directory) {
      val zipFile =
        Files.createTempFile(
          tempDir,
          locator.flatLocalCacheKey,
          ".ai2-datastore.upload.zip"
        )
      TempCleanup.remember(zipFile)
      try {
        Resource.using(new ZipOutputStream(Files.newOutputStream(zipFile))) { zip =>
          Files.walkFileTree(path, new SimpleFileVisitor[Path] {
            override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
              zip.putNextEntry(new ZipEntry(path.relativize(file).toString))
              Files.copy(file, zip)
              FileVisitResult.CONTINUE
            }

            override def preVisitDirectory(
              dir: Path,
              attrs: BasicFileAttributes
            ): FileVisitResult = {
              if (dir != path) {
                zip.putNextEntry(new ZipEntry(path.relativize(dir).toString + "/"))
              }
              FileVisitResult.CONTINUE
            }
          })
        }

        multipartUpload(zipFile, locator)
      } finally {
        Files.deleteIfExists(zipFile)
        TempCleanup.forget(zipFile)
      }
    } else {
      multipartUpload(path, locator)
    }
  }

  //
  // Checking what's in the datastore
  //

  /** Checks whether a file exists in the datastore
    *
    * @param group   group of the file in the datastore
    * @param name    name of the file in the datastore
    * @param version version of the file in the datastore
    * @return true if the file exists, false otherwise
    */
  def fileExists(group: String, name: String, version: Int): Boolean =
    exists(Locator(group, name, version, false))

  /** Checks whether a directory exists in the datastore
    *
    * @param group   group of the directory in the datastore
    * @param name    name of the directory in the datastore
    * @param version version of the directory in the datastore
    * @return true if the directory exists, false otherwise
    */
  def directoryExists(group: String, name: String, version: Int): Boolean =
    exists(Locator(group, name, version, true))

  /** Checks whether an item exists in the datastore
    *
    * @param locator locator of the item in the datastore
    * @return true if the item exists, false otherwise
    */
  def exists(locator: Locator): Boolean = withRetries(s"looking for ${locator.s3key}") {
    accessDeniedWrapper {
      try {
        s3.getObjectMetadata(bucketName, locator.s3key)
        true
      } catch {
        case e: AmazonServiceException if e.getStatusCode == 404 =>
          false
      }
    }
  }

  //
  // Listing the datastore
  //

  /** Rolls up all the listings in a paged object listing from S3
    *
    * @param request object listing request to send to S3
    * @return a sequence of object listings from S3
    */
  private def getAllListings(request: ListObjectsRequest) = accessDeniedWrapper {
    def concatenateListings(
      listings: Seq[ObjectListing],
      newListing: ObjectListing
    ): Seq[ObjectListing] = {
      val concatenation = listings :+ newListing
      if (newListing.isTruncated) {
        concatenateListings(concatenation, s3.listNextBatchOfObjects(newListing))
      } else {
        concatenation
      }
    }

    concatenateListings(Seq.empty, s3.listObjects(request))
  }

  /** Lists all groups in the datastore
    *
    * @return a set of all groups in the datastore
    */
  def listGroups: collection.SortedSet[String] = withRetries("listing all groups") {
    val listObjectsRequest =
      new ListObjectsRequest().
        withBucketName(bucketName).
        withPrefix("").
        withDelimiter("/")
    getAllListings(listObjectsRequest)
      .flatMap(_.getCommonPrefixes.asScala)
      .map(_.stripSuffix("/")).to[collection.SortedSet]
  }

  /** Lists all items in a group
    *
    * @param group group to search over
    * @return a set of locators, one for each item in the group. Multiple versions are multiple
    * locators.
    */
  def listGroupContents(group: String): collection.SortedSet[Locator] =
    withRetries(s"listing contents of group $group") {
      val listObjectsRequest =
        new ListObjectsRequest().
          withBucketName(bucketName).
          withPrefix(group + "/").
          withDelimiter("/")
      val objects = getAllListings(listObjectsRequest).flatMap(_.getObjectSummaries.asScala)
      objects.filter(_.getKey != group + "/").map { os =>
        Locator.fromKey(os.getKey)
      }.to[collection.SortedSet]
    }

  //
  // Getting URLs for datastore items
  //

  /** Gets a URL for a file in the datastore
    *
    * @param group   group of the file
    * @param name    name of the file
    * @param version version of the file
    * @return URL pointing to the file
    */
  def fileUrl(group: String, name: String, version: Int): URL =
    url(Locator(group, name, version, false))

  /** Gets a URL for a directory in the datastore
    *
    * @param group   group of the directory
    * @param name    name of the directory
    * @param version version of the directory
    * @return URL pointing to the directory. This URL will always point to a zip file containing the
    * directory's contents.
    */
  def directoryUrl(group: String, name: String, version: Int): URL =
    url(Locator(group, name, version, true))

  /** Gets the URL for an item in the datastore
    *
    * @param locator locator of the item
    * @return URL pointing to the locator
    */
  def url(locator: Locator): URL =
    new URL("http", bucketName, locator.s3key)

  //
  // Assorted stuff
  //

  /** Wipes the cache for this datastore
    */
  def wipeCache(): Unit = {
    FileUtils.deleteDirectory(cacheDir.toFile)
  }

  /** Creates the bucket backing this datastore if necessary
    *
    * You only need to call this if you're setting up a new datastore.
    */
  def createBucketIfNotExists(): Unit = withRetries(s"creating bucket $bucketName") {
    accessDeniedWrapper {
      s3.createBucket(bucketName)
    }
  }
}

private object DefaultS3 extends AmazonS3Client()

object Datastore extends Datastore("public", DefaultS3) {
  /* If you don't have logging configuration, it logs everything to stdout. In the case of the AWS
   * SDK, that means it logs every byte that goes over the wire, sometimes several times. To avoid
   * this, regardless of logging configuration, this resets the log level for the relevant
   * components to WARN if they haven't been set by anything else.
   *
   * This only works with logback.
   */
  try {
    Set("org.apache.http", "com.amazonaws").foreach { silencedLogger =>
      val awsLogger = LoggerFactory.getLogger(silencedLogger).
        asInstanceOf[ch.qos.logback.classic.Logger]
      if (awsLogger.getLevel == null) awsLogger.setLevel(Level.WARN)
    }
  } catch {
    case _: ClassCastException =>
    // do nothing
  }

  val defaultName = Datastore.name

  def apply(): Datastore = Datastore(defaultName)
  def apply(name: String): Datastore = new Datastore(name, DefaultS3)

  def apply(accessKey: String, secretAccessKey: String): Datastore =
    Datastore(defaultName, accessKey, secretAccessKey)
  def apply(name: String, accessKey: String, secretAccessKey: String): Datastore =
    new Datastore(
      name,
      new AmazonS3Client(new BasicAWSCredentials(accessKey, secretAccessKey))
    )

  def locatorFromUrl(uri: URI) = {
    def error = throw new IllegalArgumentException(s"$uri cannot be parsed as a datastore URI")

    if (uri.getScheme == "datastore") {
      val fileWithExtension = """([^/]+)/(.+)-v(\d+)\.(.*)""".r
      val fileWithoutExtension = """([^/]+)/(.+)-v(\d+)""".r
      val directory = """([^/]+)/(.+)-d(\d+)""".r

      // pattern matching on Int
      object Int {
        def unapply(s: String): Option[Int] = try {
          Some(s.toInt)
        } catch {
          case _: java.lang.NumberFormatException => None
        }
      }

      def datastore = Datastore(uri.getAuthority)
      uri.getPath.stripPrefix("/") match {
        case fileWithExtension(group, name, Int(version), ext) =>
          datastore.Locator(group, s"$name.$ext", version, false)
        case fileWithoutExtension(group, name, Int(version)) =>
          datastore.Locator(group, name, version, false)
        case directory(group, name, Int(version)) =>
          datastore.Locator(group, name, version, true)
        case _ => error
      }
    } else {
      error
    }
  }
}

object PrivateDatastore extends Datastore("private", DefaultS3)
