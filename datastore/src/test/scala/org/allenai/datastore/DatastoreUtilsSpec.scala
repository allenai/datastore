package org.allenai.datastore

import org.allenai.common.testkit.UnitSpec

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

class DatastoreUtilsSpec extends UnitSpec {
  private val datastoreName = "private"
  private val group = "org.allenai.datastoreutils.test"
  private val fileName = "testfile.txt"
  private val directoryName = "testfolder"
  private val version = 1

  "DatastoreUtils" should "correctly download Datastore file as source" in {
    val source = DatastoreUtils.getDatastoreFileAsSource(datastoreName, group, fileName, version)
    val lines = source.getLines().toVector
    val whatIsExpected = Seq("This is line 1.", "This is line 2.", "...", "This is line N.")
    lines should be(whatIsExpected)
  }

  it should "correctly download Datastore directory as folder" in {
    val folder = DatastoreUtils.getDatastoreDirectoryAsFolder(datastoreName, group, directoryName,
      version)
    val files = folder.listFiles
    val filenames = files.map(_.getName)
    filenames should be(Seq("testfile1.txt", "testfile2.txt"))
  }

  private val filePropertyMap: Map[String, AnyRef] = Map(
    "datastore" -> "private",
    "group" -> "org.allenai.datastoreutils.test",
    "name" -> "testfile.txt",
    "version" -> "1"
  )

  private val folderPropertyMap: Map[String, AnyRef] = Map(
    "datastore" -> "private",
    "group" -> "org.allenai.datastoreutils.test",
    "name" -> "testfolder",
    "version" -> "1"
  )

  private val fileConfig = ConfigFactory.parseMap(filePropertyMap.asJava)
  private val folderConfig = ConfigFactory.parseMap(folderPropertyMap.asJava)

  it should "correctly download Datastore file as source when given a Config" in {
    val source = DatastoreUtils.getDatastoreFileAsSource(fileConfig)
    val lines = source.getLines().toVector
    val whatIsExpected = Seq("This is line 1.", "This is line 2.", "...", "This is line N.")
    lines should be(whatIsExpected)
  }

  it should "correctly download Datastore directory as folder when given a Config" in {
    val folder = DatastoreUtils.getDatastoreDirectoryAsFolder(folderConfig)
    val files = folder.listFiles
    val filenames = files.map(_.getName)
    filenames should be(Seq("testfile1.txt", "testfile2.txt"))
  }
}
