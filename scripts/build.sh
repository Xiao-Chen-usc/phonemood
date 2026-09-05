#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x '/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home/bin/java' ]]; then
  export JAVA_HOME='/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home'
fi
if [[ $# -eq 0 ]]; then
  set -- :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
fi
exec ./gradlew "$@"
