package org.allenai.datastore

import org.allenai.common.testkit.UnitSpec

class LocatorSpec extends UnitSpec {
  "Locator.fromUri" should "parse a file URI correctly" in {
    val locator = Locator.fromUri("datastore://public/test.bucket/filename-v2.txt")
    locator.datastore should be("public")
    locator.group should be("test.bucket")
    locator.filename should be("filename.txt")
    locator.version should be(2)
    locator.directory should be(false)
  }

  it should "parse a directory URI correctly" in {
    val locator = Locator.fromUri("datastore://public/test.bucket/dirname-d3")
    locator.datastore should be("public")
    locator.group should be("test.bucket")
    locator.filename should be("dirname")
    locator.version should be(3)
    locator.directory should be(true)
  }

  it should "parse an extensionless file correctly" in {
    val locator = Locator.fromUri("datastore://public/test.bucket/bareword-v8")
    locator.datastore should be("public")
    locator.group should be("test.bucket")
    locator.filename should be("bareword")
    locator.version should be(8)
    locator.directory should be(false)
  }

  "Locator.uri" should "produce a valid file URI" in {
    val locator = Locator("private", "uri.bucket", "file.test", 1, false)
    locator.uri.toString should be("datastore://private/uri.bucket/file-v1.test")
  }

  it should "produce a valid directory URI" in {
    val locator = Locator("private", "uri.bucket", "dir_test", 1, true)
    locator.uri.toString should be("datastore://private/uri.bucket/dir_test-d1")
  }
}
