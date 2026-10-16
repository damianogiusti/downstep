#!/bin/sh
# Starts the app from the sources.
. "$(dirname "$0")/common.sh"
check_toolchain
gradle :desktopApp:run