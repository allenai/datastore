package sun.net.www.protocol.datastore

import org.allenai.datastore.Datastore

import sun.net.www.protocol.file.FileURLConnection

import java.net.{URLConnection, URL, URLStreamHandler}

class Handler extends URLStreamHandler {
  override def openConnection(u: URL): URLConnection = {
    val datastore = Datastore(u.getAuthority)
    val withExtension = """([^/]+)/(.+)-v(\d+)\.(.*)""".r
    val withoutExtension = """([^/]+)/(.+)-v(\d+)""".r

    // pattern matching on Int
    object Int {
      def unapply(s: String): Option[Int] = try {
        Some(s.toInt)
      } catch {
        case _: java.lang.NumberFormatException => None
      }
    }

    val path = u.getPath.stripPrefix("/") match {
      case withExtension(group, name, Int(version), ext) =>
        datastore.filePath(group, s"$name.$ext", version)
      case withoutExtension(group, name, Int(version)) =>
        datastore.filePath(group, name, version)
      case _ =>
        throw new IllegalArgumentException(s"$u cannot be parsed as a datastore URI")
    }

    path.toUri.toURL.openConnection()
  }
}
