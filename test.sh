#!/usr/bin/env bash
# Compile and run the mod in a dev server, then exit.
#
# Two legs: prefix the task with the version project. Examples:
#   ./test.sh :1.21.1:test            # unit tests, 1.21.1 leg
#   ./test.sh :1.21.1:runClient       # dev client, 1.21.1
#   ./test.sh :1.20.1:runServer       # dev server, 1.20.1
#
# This is the harness for automated verification. For interactive playtesting a real client
# and a dedicated server, use build.sh + the matching test-server directory instead.
set -euo pipefail

cd "$(dirname "$0")"

# runGameTestServer boots the dedicated server in gametest mode and exits once tests finish.
# With no gametests registered it will fail, which is expected at this stage of the project.
./gradlew "$@"
