#!/usr/bin/env bash
# Build one leg of the mod and copy its jar into the matching test-server/mods.
# Usage: ./build.sh [1.21.1|1.20.1]   (default: 1.21.1)
#
# Two legs, two jars, two servers: the 1.21.1 jar only runs on NeoForge 1.21.1 and the
# 1.20.1 jar only on Forge 47.x, so each leg has its own test-server directory and the
# script never mixes them. Only ever removes our own previous jar, never another mod's.
set -euo pipefail

cd "$(dirname "$0")"

MC="${1:-1.21.1}"
case "$MC" in
  1.21.1) SERVER="test-server" ;;
  1.20.1) SERVER="test-server-1.20.1" ;;
  *) echo "unknown version: $MC (use 1.21.1 or 1.20.1)" >&2; exit 1 ;;
esac

./gradlew ":$MC:build"

JAR=$(ls -t "versions/$MC/build/libs/"cortescombat-*.jar 2>/dev/null | grep -v -e '-sources' -e '-javadoc' | head -n 1)
if [ -z "$JAR" ]; then
  echo "No jar found in versions/$MC/build/libs" >&2
  exit 1
fi

mkdir -p "$SERVER/mods"
find "$SERVER/mods" -name 'cortescombat-*.jar' -delete
cp "$JAR" "$SERVER/mods/"

echo "Copied $JAR -> $SERVER/mods/"
