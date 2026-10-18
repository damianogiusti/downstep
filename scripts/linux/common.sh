# Shared by the Linux and macOS scripts (sourced). POSIX sh.

set -eu

target=$(basename "$0" .sh)
repo_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

# Gradle from the repository root, with -PappVersion when APP_VERSION is set and -PreleaseRepo when
# RELEASE_REPO is set (as in release.yml). A script can set gradle_wrap to run it through another
# command (xvfb-run).
gradle() {
    if [ -n "${APP_VERSION:-}" ]; then
        set -- "$@" "-PappVersion=$APP_VERSION"
    fi
    if [ -n "${RELEASE_REPO:-}" ]; then
        set -- "$@" "-PreleaseRepo=$RELEASE_REPO"
    fi
    ${gradle_wrap:-} sh ./gradlew "$@"
}

# JDK 23 and the Android SDK for Gradle, CMake 3.30+ and a C++20 compiler for the engine.
check_toolchain() {
    for tool in java cmake c++; do
        if ! command -v "$tool" > /dev/null 2>&1; then
            echo "$tool not found: see README, Build (or run make docker-$target)." >&2
            exit 1
        fi
    done
    if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
        echo "Android SDK not found: set ANDROID_HOME (Gradle needs it to configure the shared modules)." >&2
        exit 1
    fi
}