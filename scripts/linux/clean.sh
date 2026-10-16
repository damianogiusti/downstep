#!/bin/sh
# Removes what the build made: Gradle's outputs (the engine's CMake folder too) and build/ at the
# root (packages and what the container copied back). The Docker volumes stay: make docker-clean.
. "$(dirname "$0")/common.sh"
check_toolchain
gradle clean
rm -rf build