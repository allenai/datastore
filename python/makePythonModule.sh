#!/bin/bash

set -x
set -e

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
pushd $SCRIPTDIR/..
rm -rf datastore/target
sbt 'datastore/package'
DEPENDENT_JARS=$(sbt 'project datastore' 'show fullClasspath' | grep -o 'Attributed([^)]*)' | grep '.jar)$' | sed -e 's/Attributed(\([^)]*\))/--include \1/' | paste -s -d" " -)
DATASTORE_JAR=$SCRIPTDIR/../$(echo datastore/target/scala-2.11/datastore_2.11-*.jar)
VERSION=$(cat version.sbt | sed -e 's/version in ThisBuild := "\(.*\)"/\1/' | head)
popd

rm -rf build/ dist/ *.egg-info/

python -m jcc --include $DATASTORE_JAR $DEPENDENT_JARS \
  'org.allenai.datastore.Datastore' 'org.allenai.datastore.Datastore$' 'java.nio.file.Path' \
  --maxheap 1G \
  --rename 'java.util.Iterator=JavaIterator,scala.collection.AbstractMap=ScalaAbstractMap,java.lang.Iterable=JavaIterable' \
  --python datastore \
  --version $VERSION \
  --build --bdist --extra-setup-arg bdist_wheel

echo "The wheel is at" dist/*.whl "."
