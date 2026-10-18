# Downstep's tasks: make help lists them. The recipes only call the scripts in scripts/windows
# (PowerShell) or scripts/linux (sh, also for macOS), so make and just do the same things.
# docker-* run the Linux build in a container (docker/Dockerfile), from any host with Docker.

.DEFAULT_GOAL := help

# make package APP_VERSION=1.2.3 RELEASE_REPO=owner/name: the version, and the GitHub repository the
# app updates from. The scripts pass them to Gradle; both are optional.
export APP_VERSION RELEASE_REPO

ifeq ($(OS),Windows_NT)
# PowerShell, never sh: GNU make for Windows would take sh.exe from PATH (Git's, for example) when
# SHELL is not set. Windows PowerShell is always there; PowerShell 7 is used when installed.
SHELL := powershell.exe
.SHELLFLAGS := -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command
ifndef PWSH
PWSH := $(if $(shell Get-Command pwsh.exe -ErrorAction Ignore),pwsh.exe,powershell.exe)
endif
SHELL := $(PWSH)
script = & ./scripts/windows/$(1).ps1
docker = & ./scripts/windows/docker.ps1 $(1)
else
SHELL := /bin/sh
script = sh ./scripts/linux/$(1).sh
docker = sh ./scripts/linux/docker.sh $(1)
endif

.PHONY: help build test engine engine-test package run clean \
	docker-image docker-build docker-test docker-engine docker-engine-test docker-package docker-shell docker-clean

help: ## List the targets
	@$(call script,help)

build: ## Compile the desktop app and the native engine
	$(call script,build)

test: ## Run the Kotlin tests of every module
	$(call script,test)

engine: ## Build the native engine (engine-desktop/build/native)
	$(call script,engine)

engine-test: ## Build and run the engine's C++ tests
	$(call script,engine-test)

package: ## Package the app in build/dist: zip (Windows), tar.gz and .deb (Linux), DMG (macOS)
	$(call script,package)

run: ## Start the app from the sources
	$(call script,run)

clean: ## Remove the build outputs
	$(call script,clean)

docker-image: ## Build the Linux build image (docker/Dockerfile)
	$(call docker,image)

docker-build: ## build, in the Linux container
	$(call docker,build)

docker-test: ## test, in the Linux container (reports in build/docker-out/reports)
	$(call docker,test)

docker-engine: ## engine, in the Linux container (libsdengine.so in build/docker-out/engine)
	$(call docker,engine)

docker-engine-test: ## engine-test, in the Linux container
	$(call docker,engine-test)

docker-package: ## package, in the Linux container (build/docker-out/dist)
	$(call docker,package)

docker-shell: ## A shell in the container's copy of the sources
	$(call docker,shell)

docker-clean: ## Remove the container's volumes (Gradle caches, build) and build/docker-out
	$(call docker,clean)