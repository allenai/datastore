package org.allenai.datastore

import java.net.URI
import java.nio.file.{ Path, Paths }

/** Identifies a single version of a file or directory in a datastore. Locators have a unique URI
  * representation:
  * - the scheme is "datastore"
  * - the authority is the datastore name
  * - the first path element is the group
  * - the second and final path element is the filename with version
  *
  * The filename is constructed as follows:
  * - directories are of the form "$filename-d$version"
  * - files without extensions are of the form "$filename-v$version"
  * - files with extensions are of the form "${base(filename)}-v$version.${extension(filename)}"
  */
case class Locator(
    datastore: String,
    group: String,
    filename: String,
    version: Int,
    directory: Boolean
) {
  require(!datastore.contains("/"))
  require(!group.contains("/"))
  require(!filename.contains("/"))
  require(version > 0)

  private[datastore] def nameWithVersion: String = {
    if (directory) {
      s"$filename-d$version.zip"
    } else {
      val lastDotIndex = filename.lastIndexOf('.')
      if (lastDotIndex < 0) {
        s"$filename-v$version"
      } else {
        filename.substring(0, lastDotIndex) + s"-v$version" + filename.substring(lastDotIndex)
      }
    }
  }
  private[datastore] def s3key: String = s"$group/$nameWithVersion"
  private[datastore] def localCacheKey: String =
    if (directory) s3key.stripSuffix(".zip") else s3key
  private[datastore] def flatLocalCacheKey: String = localCacheKey.replace('/', '%')
  private[datastore] def localCachePath: Path = {
    Locator.cacheDirFor(datastore).resolve(localCacheKey)
  }
  private[datastore] def lockfilePath: Path = {
    Locator.cacheDirFor(datastore).resolve(localCacheKey + ".lock")
  }

  def uri: URI = new URI("datastore", datastore, "/" + localCacheKey, null)
}
object Locator {
  implicit val defaultOrdering = new Ordering[Locator] {
    def compare(x: Locator, y: Locator): Int = {
      val ordering = implicitly[Ordering[(String, Int, Boolean)]]
      ordering.compare((x.filename, x.version, x.directory), (y.filename, y.version, y.directory))
    }
  }
  val FileWithExtension = """([^/]+)/(.+)-v(\d+)\.(.*)""".r
  val FileWithoutExtension = """([^/]+)/(.+)-v(\d+)""".r
  val Directory = """([^/]+)/(.+)-d(\d+)""".r
  def fromUri(uri: String): Locator = fromUri(new URI(uri))
  def fromUri(uri: URI): Locator = {
    def error = throw new IllegalArgumentException(s"$uri cannot be parsed as a datastore URI")

    if (uri.getScheme == "datastore") {
      // pattern matching on Int
      object Int {
        def unapply(s: String): Option[Int] = try {
          Some(s.toInt)
        } catch {
          case _: java.lang.NumberFormatException => None
        }
      }

      val datastore = uri.getAuthority
      uri.getPath.stripPrefix("/") match {
        case FileWithExtension(group, name, Int(version), ext) => {
          Locator(datastore, group, s"$name.$ext", version, false)
        }
        case FileWithoutExtension(group, name, Int(version)) => {
          Locator(datastore, group, name, version, false)
        }
        case Directory(group, name, Int(version)) => {
          Locator(datastore, group, name, version, true)
        }
        case _ => error
      }
    } else {
      error
    }
  }

  val KeyWithExtension = """([^/]*)/(.*)-(.)(\d*)\.(.*)""".r
  val KeyWithoutExtension = """([^/]*)/(.*)-(.)(\d*)""".r

  /** @return the locator for the given key and datastore name */
  private[datastore] def fromKey(datastore: String, key: String): Locator = {
    // pattern matching on Int
    object Int {
      def unapply(s: String): Option[Int] = try {
        Some(s.toInt)
      } catch {
        case _: java.lang.NumberFormatException => None
      }
    }

    key match {
      case KeyWithExtension(group, filename, "v", Int(version), ext) => {
        Locator(datastore, group, s"$filename.$ext", version, false)
      }
      case KeyWithoutExtension(group, filename, "v", Int(version)) => {
        Locator(datastore, group, filename, version, false)
      }
      case KeyWithExtension(group, filename, "d", Int(version), "zip") => {
        Locator(datastore, group, filename, version, true)
      }
      case _ => {
        throw new IllegalArgumentException(s"$key cannot be parsed as a datastore key")
      }
    }
  }

  private[datastore] val BaseCacheDir = {
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

  private[datastore] def cacheDirFor(datastore: String): Path = BaseCacheDir.resolve(datastore)
}
