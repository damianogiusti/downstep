#!/bin/sh
# Compiles the desktop app and everything it uses, with the native engine.
. "$(dirname "$0")/common.sh"
check_toolchain
gradle :engine-desktop:buildNativeEngine :desktopApp:jar