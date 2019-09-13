package sun.net.www.protocol.datastore

import java.net.{ URL, URLConnection, URLStreamHandler }

import org.allenai.datastore.Datastore

class Handler extends URLStreamHandler {
  override def openConnection(u: URL): URLConnection = {
    val datastore = Datastore(u.getAuthority)
    val fileWithExtension = """([^/]+)/(.+)-v(\d+)\.(.*)""".r
    val fileWithoutExtension = """([^/]+)/(.+)-v(\d+)""".r
    val directory = """([^/]+)/(.+)-d(\d+)(?:/(.*))?""".r

    // pattern matching on Int
    object Int {
      def unapply(s: String): Option[Int] =
        try {
          Some(s.toInt)
        } catch {
          case _: java.lang.NumberFormatException => None
        }
    }

    val path = u.getPath.stripPrefix("/") match {
      case fileWithExtension(group, name, Int(version), ext) =>
        datastore.filePath(group, s"$name.$ext", version)
      case fileWithoutExtension(group, name, Int(version)) =>
        datastore.filePath(group, name, version)
      case directory(group, name, Int(version), null) =>
        datastore.directoryPath(group, name, version)
      case directory(group, name, Int(version), innerPath) =>
        datastore.directoryPath(group, name, version).resolve(innerPath)
      case _ =>
        throw new IllegalArgumentException(s"$u cannot be parsed as a datastore URI")
    }

    path.toUri.toURL.openConnection()
  }
}
