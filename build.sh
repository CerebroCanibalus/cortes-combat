#!/usr/bin/env bash
# Build one leg of the mod and copy its jar into the matching test-server/mods.
# Usage: ./build.sh [1.21.1|1.20.1]   (default: 1.21.1)
#
# Two legs, two jars, two servers: the 1.21.1 jar only runs on NeoForge 1.21.1 and the
# 1.20.1 jar only on Forge 47.x, so each leg has its own test-server directory and the
# script never mixes them. Only ever removes our own previous jar, never another mod's.
set -euo pipefail
# Case-insensitive: the jar is Cortes-combat-*.jar and the glob below is lowercase.
shopt -s nocaseglob

cd "$(dirname "$0")"

MC="${1:-1.21.1}"
case "$MC" in
  1.21.1) SERVER="test-server"; CC="" ;;
  # ForgeGradle 6 breaks the configuration-cache store (its tasks call Task.project
  # at execution time), so the 1.20.1 leg builds without it. The jar is complete
  # before the cache step fails, but a red build that made a jar is still a red
  # build -- hence the flag, not an || true.
  1.20.1) SERVER="test-server-1.20.1"; CC="--no-configuration-cache" ;;
  *) echo "unknown version: $MC (use 1.21.1 or 1.20.1)" >&2; exit 1 ;;
esac

# shellcheck disable=SC2086
./gradlew ":$MC:build" $CC

JAR=$(ls -t "versions/$MC/build/libs/"*cortes-combat-*-all.jar 2>/dev/null | grep -v -e '-sources' -e '-javadoc' | head -n 1)
if [ -z "$JAR" ]; then
  # Sin clasificador -all: pata sin JarJar (o jar viejo de antes del cambio).
  JAR=$(ls -t "versions/$MC/build/libs/"*cortes-combat-*.jar 2>/dev/null | grep -v -e '-sources' -e '-javadoc' -e '-all' | head -n 1)
fi
if [ -z "$JAR" ]; then
  echo "No jar found in versions/$MC/build/libs" >&2
  exit 1
fi

mkdir -p "$SERVER/mods"
find "$SERVER/mods" -iname '*cortes-combat-*.jar' -delete
cp "$JAR" "$SERVER/mods/"

echo "Copied $JAR -> $SERVER/mods/"
