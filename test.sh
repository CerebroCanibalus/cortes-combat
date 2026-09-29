#!/usr/bin/env bash
# Compile and run the mod in a dev server, then exit.
#
# This is the harness for automated verification. For interactive playtesting a real client
# and a dedicated server, use the dedicated scripts build.sh + test-server instead.
set -euo pipefail

cd "$(dirname "$0")"

# runGameTestServer boots the dedicated server in gametest mode and exits once tests finish.
# With no gametests registered it will fail, which is expected at this stage of the project.
./gradlew "$@"
