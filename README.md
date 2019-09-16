datastore
======

**Boss**: Dirk

A thin wrapper over S3 that stores and retrieves immutable data in S3 and supports versioning.

The real READMEs are [here for the API](datastore/README.md) and [here for the CLI](datastore-cli/README.md).

### Releasing

This project releases to BinTray.  To make a release:

1. Pull the latest code on the master branch that you want to release
1. Edit `build.sbt` to remove "-SNAPSHOT" from the current version
1. Create a pull request if desired or push to master if you are only changing the version
1. Tag the release `git tag -a vX.Y.Z -m "Release X.Y.Z"` replacing X.Y.Z with the correct version
1. Push the tag back to origin `git push origin vX.Y.Z`
1. Release the build on Bintray `sbt +publish` (the "+" is required to cross-compile)
1. Verify publication [on bintray.com](https://bintray.com/allenai/maven)
1. Bump the version in `build.sbt` on master (and push!) with X.Y.Z+1-SNAPSHOT (e.g., 2.5.1
-SNAPSHOT after releasing 2.5.0)

If you make a mistake you can rollback the release with `sbt bintrayUnpublish` and retag the
 version to a different commit as necessary.
