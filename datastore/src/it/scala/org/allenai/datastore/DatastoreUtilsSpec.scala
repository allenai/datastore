package org.allenai.datastore

import org.allenai.common.testkit.UnitSpec

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

  private val fileUriString = "datastore://private/org.allenai.datastoreutils.test/testfile-v1.txt"
  private val folderUriString = "datastore://private/org.allenai.datastoreutils.test/testfolder-v1"

  it should "correctly download Datastore file as source when given a URI" in {
    val source = DatastoreUtils.getDatastoreFileAsSource(fileUriString)
    val lines = source.getLines().toVector
    val whatIsExpected = Seq("This is line 1.", "This is line 2.", "...", "This is line N.")
    lines should be(whatIsExpected)
  }

  it should "correctly download Datastore directory as folder when given a URI" in {
    val folder = DatastoreUtils.getDatastoreDirectoryAsFolder(folderUriString)
    val files = folder.listFiles
    val filenames = files.map(_.getName)
    filenames should be(Seq("testfile1.txt", "testfile2.txt"))
  }
}
