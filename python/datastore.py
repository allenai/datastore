#!/usr/bin/python3
import logging
from typing import *
import boto3
import platform
from pathlib import Path
import os
import time
import atexit
import shutil
import tempfile
import zipfile
import botocore


def _mkpath(p: Path) -> None:
    p.mkdir(mode=0o755, parents=True, exist_ok=True)

#
# Cleanup stuff
#

_cleanup_paths: Set[Path] = set()

def _cleanup_cleanup_paths() -> None:
    global _cleanup_paths
    for path in _cleanup_paths:
        assert path.is_absolute()   # safety
        shutil.rmtree(path)
    _cleanup_paths = set()
atexit.register(_cleanup_cleanup_paths)

def remember_cleanup(p: Union[Path, str]) -> None:
    global _cleanup_paths
    if type(p) is str:
        p = Path(p)
    _cleanup_paths.add(p.absolute())

def forget_cleanup(p: Union[Path, str]) -> None:
    global _cleanup_paths
    if type(p) is str:
        p = Path(p)
    _cleanup_paths.remove(p)

#
# Datastore stuff
#

_s3 = boto3.resource('s3')

class Locator(NamedTuple):
    group: str
    name: str
    version: int
    directory: bool

    def name_with_version(self) -> str:
        if self.directory:
            return f"{self.name}-d{self.version}.zip"
        else:
            try:
                last_dot_index = self.name.rindex('.')
            except ValueError:
                return f"{self.name}-v{self.version}"
            return f"{self.name[:last_dot_index]}-v{self.version}{self.name[last_dot_index:]}"

    def s3_key(self):
        return f"{self.group}/{self.name_with_version()}"

    def local_cache_key(self):
        r = self.s3_key()
        if self.directory and r.endswith(".zip"):
            r = r[:-(len(".zip"))]
        return r

    def flat_local_cache_key(self):
        return self.local_cache_key().replace('/', '%')

class DsError(Exception):
    pass

class DoesNotExistError(DsError):
    pass

class AlreadyExistsError(DsError):
    pass

class AccessDeniedError(DsError):
    pass

class Datastore:
    def __init__(self, name: str):
        self.cache_dir = os.environ.get('AI2_DATASTORE_DIR')
        if self.cache_dir is not None:
            self.cache_dir = Path(self.cache_dir)
        else:
            self.cache_dir = Path.home()
            if platform.system() == 'Darwin':
                self.cache_dir = self.cache_dir / "Library" / "Caches" / "org.allenai.datastore"
            elif platform.system() == 'Linux':
                self.cache_dir = self.cache_dir / ".ai2" / "datastore"
            else:
                raise ValueError("Unsupported platform: %s" % platform.system())
        self.temp_dir = self.cache_dir / "tmp" # temp has to be on the same filesystem as the cache itself, so that's why we put it here
        self.cache_dir = self.cache_dir / name

        _mkpath(self.temp_dir)

        self.bucket = _s3.Bucket(f"{name}.store.dev.allenai.org")

        self.logger = logging.getLogger(f"org.allenai.datastore.Datastore.{name}")

    def _local_cache_path(self, locator: Locator) -> Path:
        return self.cache_dir / locator.local_cache_key()

    def _lockfile_path(self, locator: Locator) -> Path:
        return self.cache_dir / (locator.local_cache_key() + ".lock")

    def _wait_for_lockfile(self, lockfile_path: Path) -> None:
        """Wait until the given lockfile no longer exists."""
        if not lockfile_path.exists():
            return

        # The first second is free.
        start = time.time()
        time.sleep(1)
        if not lockfile_path.exists():
            return

        # After the first second, we print one message, then we stay silent for 10 minutes, at
        # which time we print a message every minute.
        def time_elapsed() -> float:
            return time.time() - start
        self.logger.info("Starting to wait for %s", lockfile_path)
        next_message_time = time.time() + 16 * 60
        while lockfile_path.exists():
            if next_message_time - time.time() < 0:
                self.logger.warning(
                    "Lockfile %s has been blocked for %.0f seconds",
                    lockfile_path,
                    time_elapsed())
                next_message_time = time.time() + 60
            time.sleep(1)

    def file(self, group: str, name: str, version: int) -> Path:
        return self.path(Locator(group, name, version, False))

    def directory(self, group: str, name: str, version: int) -> Path:
        return self.path(Locator(group, name, version, True))

    def path(self, locator: Locator) -> Path:
        _mkpath(self.cache_dir)
        lockfile_path = self._lockfile_path(locator)
        _mkpath(lockfile_path.parent)
        self._wait_for_lockfile(lockfile_path)

        local_cache_path = self._local_cache_path(locator)
        if locator.directory and local_cache_path.is_dir():
            return local_cache_path
        elif not locator.directory and local_cache_path.is_file():
            return local_cache_path

        try:
            lockfile_path.touch(mode=0o644, exist_ok=False)
        except FileExistsError:
            return self.path(locator)

        remember_cleanup(lockfile_path)
        try:
            # We're downloading to a temp file first. If we were downloading into the file directly,
            # and we died half-way through the download, we'd leave half a file, and that's not
            # good.
            temp_file = tempfile.NamedTemporaryFile(
                dir=self.temp_dir,
                prefix=f"ai2-datastore-{locator.flat_local_cache_key()}",
                suffix=".tmp",
                delete=False)
            try:
                remember_cleanup(temp_file.name)
                try:
                    # TODO: retries
                    self.bucket.download_fileobj(locator.s3_key(), temp_file)
                except botocore.exceptions.ClientError as e:
                    e = e.response
                    if e is None:
                        raise
                    e = e.get('Error')
                    if e is None:
                        raise
                    e = e.get('Code')
                    if e is None:
                        raise
                    if e == '404':
                        raise DoesNotExistError()
                    else:
                        raise

                temp_file.seek(0)

                if locator.directory:
                    temp_zip_dir = tempfile.mkdtemp(
                        dir=self.temp_dir,
                        prefix=f"ai2-datastore-{locator.flat_local_cache_key()}")
                    remember_cleanup(temp_zip_dir)

                    with zipfile.ZipFile(temp_file) as zip_file:
                        zip_file.extractall(temp_zip_dir)

                    Path(temp_zip_dir).rename(local_cache_path)
                    forget_cleanup(temp_zip_dir)
                else:
                    _mkpath(local_cache_path.parent)
                    temp_file.close()
                    Path(temp_file.name).rename(local_cache_path)
                    forget_cleanup(temp_file.name)
                    temp_file = None
            finally:
                if temp_file is not None:
                    temp_file.close()
                    os.remove(temp_file.name)
                    forget_cleanup(temp_file.name)
        finally:
            lockfile_path.unlink()
            forget_cleanup(lockfile_path)

        return local_cache_path

public = Datastore("public")
private = Datastore("private")

import re
_datastore_file_with_extension = re.compile("^datastore://([^/]+)/([^/]+)/(.+)-v(\d+)\.(.*)$")
_datastore_file_without_extension = re.compile("^datastore://([^/]+)/([^/]+)/(.+)-v(\d+)$")
_datastore_directory = re.compile("^datastore://([^/]+)/([^/]+)/(.+)-d(\d+)(?:/(.*))?$")
_datastore_map = {
    "public": public,
    "private": private
}
def resolve_url(url_or_filename: str) -> str:
    match = _datastore_file_with_extension.match(url_or_filename)
    if match:
        (ds, group, name, version, extension) = match.groups()
        ds = _datastore_map[match.groups()[0]]
        name = "%s.%s" % (name, extension)
        version = int(version)
        return str(ds.file(group, name, version))

    match = _datastore_file_without_extension.match(url_or_filename)
    if match:
        (ds, group, name, version) = match.groups()
        ds = _datastore_map[match.groups()[0]]
        version = int(version)
        return str(ds.file(group, name, version))

    match = _datastore_directory.match(url_or_filename)
    if match:
        (ds, group, name, version, file_inside_directory) = match.groups()
        ds = _datastore_map[match.groups()[0]]
        version = int(version)
        return str(ds.directory(group, name, version) / file_inside_directory)

    return url_or_filename
