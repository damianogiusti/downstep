# Downstep's tasks: just --list lists them. The same recipes as the Makefile, calling the same
# scripts: scripts/windows (PowerShell, never sh or bash) or scripts/linux (sh, also for macOS).
# docker-* run the Linux build in a container (docker/Dockerfile), from any host with Docker.

set windows-shell := ["pwsh.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command"]
set shell := ["sh", "-eu", "-c"]

script := if os_family() == "windows" { "& ./scripts/windows/" } else { "sh ./scripts/linux/" }
ext := if os_family() == "windows" { ".ps1" } else { ".sh" }
docker := if os_family() == "windows" { "& ./scripts/windows/docker.ps1" } else { "sh ./scripts/linux/docker.sh" }
# PowerShell runs a quoted path only after &.
call := if os_family() == "windows" { "& " } else { "" }

# List the recipes
help:
    @{{ call }}{{ quote(just_executable()) }} --list --unsorted

# Compile the desktop app and the native engine
build:
    {{ script }}build{{ ext }}

# Run the Kotlin tests of every module
test:
    {{ script }}test{{ ext }}

# Build the native engine (engine-desktop/build/native)
engine:
    {{ script }}engine{{ ext }}

# Build and run the engine's C++ tests
engine-test:
    {{ script }}engine-test{{ ext }}

# Package the app in build/dist: zip (Windows), tar.gz and .deb (Linux), DMG (macOS)
package:
    {{ script }}package{{ ext }}

# Start the app from the sources
run:
    {{ script }}run{{ ext }}

# Remove the build outputs
clean:
    {{ script }}clean{{ ext }}

# Build the Linux build image (docker/Dockerfile)
docker-image:
    {{ docker }} image

# build, in the Linux container
docker-build:
    {{ docker }} build

# test, in the Linux container (reports in build/docker-out/reports)
docker-test:
    {{ docker }} test

# engine, in the Linux container (libsdengine.so in build/docker-out/engine)
docker-engine:
    {{ docker }} engine

# engine-test, in the Linux container
docker-engine-test:
    {{ docker }} engine-test

# package, in the Linux container (build/docker-out/dist)
docker-package:
    {{ docker }} package

# A shell in the container's copy of the sources
docker-shell:
    {{ docker }} shell

# Remove the container's volumes (Gradle caches, build) and build/docker-out
docker-clean:
    {{ docker }} clean