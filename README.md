datastore
======

**Boss**: Dirk

A thin wrapper over S3 that stores and retrieves immutable data in S3 and supports versioning.

The real READMEs are [here for the API](datastore/README.md) and [here for the CLI](datastore-cli/README.md).

### Releasing

To set up for release, follow the instructions [in the `nlpstack` readme](https://github.com/allenai/nlpstack#releasing-new-versions).

The release instructions are also accurate, although you need to set your deploy branch's upstream to `git@github.com:allenai/datastore.git/master`.

Assuming `git@github.com:allenai/datastore.git` is set to your remote `upstream`, this can be done with:
```bash
# From your deploy branch.
git branch -u upstream/master
```
