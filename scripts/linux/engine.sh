#!/bin/sh
# The native engine (libsdengine.so, or .dylib on macOS) in engine-desktop/build/native. Gradle
# drives CMake, so this is the same build the tests and the app use.
. "$(dirname "$0")/common.sh"
check_toolchain
gradle :engine-desktop:buildNativeEngine