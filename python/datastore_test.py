import unittest
import datastore
from typing import *

class TestDatastore(unittest.TestCase):
    """These tests require access to the public AI2 datastore."""

    def test_download_file(self):
        p = datastore.public.file("org.allenai.datastore", "DatastoreCli.jar", 1)
        assert p.is_file()

    def test_download_directory(self):
        p = datastore.public.directory("org.allenai.datastore", "TestDirectory", 1)
        assert p.is_dir()
        assert (p / "1.gif").is_file()
