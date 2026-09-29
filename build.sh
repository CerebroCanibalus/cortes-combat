#!/usr/bin/env bash
# Build the mod and copy the jar into test-server/mods for manual playtesting.
set -euo pipefail

cd "$(dirname "$0")"

./gradlew build

JAR=$(ls -t build/libs/cortescombat-*.jar | grep -v -e '-sources' -e '-javadoc' | head -n 1)
if [ -z "$JAR" ]; then
  echo "No jar found in build/libs" >&2
  exit 1
fi

mkdir -p test-server/mods
# Only ever remove our own previous jar, never another mod's.
find test-server/mods -name 'cortescombat-*.jar' -delete
cp "$JAR" test-server/mods/

echo "Copied $JAR -> test-server/mods/"
