Install this library like this:
```
pip install https://github.com/allenai/datastore/releases/download/v3.2.0/datastore-3.2.0-py3-none-any.whl
```

Then, in Python, you can say this:
```
>>> import datastore
>>> p = datastore.public.file("org.allenai.datastore", "DatastoreCli.jar", 1)  # download a file
>>> p = datastore.public.directory("org.allenai.datastore", "TestDirectory", 1)  # download a directory
```

The Python version of the datastore cannot wipe the cache, list contents of the datastore, or
publish new files and directories. For that, you have to use the java version or the command line.
Pull requests are welcome!
