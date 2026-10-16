#!/bin/sh
# The engine's C++ tests (engine/tests), in the build folder Gradle configured for the engine.
. "$(dirname "$0")/common.sh"
check_toolchain
gradle :engine-desktop:configureNativeEngine
jobs=$(getconf _NPROCESSORS_ONLN 2> /dev/null || echo 4)
cmake --build engine-desktop/build/native --config Release --target sdengine_tests -j "$jobs"
engine-desktop/build/native/sdengine_tests