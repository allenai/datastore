from . import initVM
initVM()

from . import Datastore as JavaDatastore

class Datastore(object):
  def __init__(self, ds: JavaDatastore):
    self.ds = ds

  def file(self, group: str, name: str, version: int) -> str:
    return self.ds.filePath(group, name, version).toString()

  def directory(self, group: str, name: str, version: int) -> str:
    return self.df.directoryPath(group, name, version).toString()

public = Datastore(JavaDatastore.apply("public"))
private = Datastore(JavaDatastore.apply("private"))
