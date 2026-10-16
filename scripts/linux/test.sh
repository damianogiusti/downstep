#!/bin/sh
# The Kotlin tests of every module, the same set as the README. The desktop ones load the engine.
# --continue: every module runs, also after a failure.
. "$(dirname "$0")/common.sh"
check_toolchain
# The Compose and Swing tests want a screen: a virtual one when there is none (the container).
if [ "$(uname -s)" = Linux ] && [ -z "${DISPLAY:-}" ] && command -v xvfb-run > /dev/null 2>&1; then
    gradle_wrap="xvfb-run -a"
fi
gradle --continue :shared:jvmTest :shared-data:desktopTest :shared-ui:desktopTest :engine-desktop:test :desktopApp:test