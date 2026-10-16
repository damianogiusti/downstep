#!/bin/sh
# The app for this system, in build/dist. Linux: the app folder as a .tar.gz, with the licenses
# also at the top as in the Windows zip, and a .deb. macOS: the DMG, as in release.yml.
# APP_VERSION sets the version (1.0.0 otherwise).
. "$(dirname "$0")/common.sh"
check_toolchain
dist=build/dist
mkdir -p "$dist"
binaries=desktopApp/build/compose/binaries/main

case $(uname -s) in
    Darwin)
        gradle :desktopApp:packageDmg
        rm -f "$dist/Downstep-mac.dmg"
        mv "$binaries"/dmg/Downstep-*.dmg "$dist/Downstep-mac.dmg"
        ;;
    Linux)
        case $(uname -m) in
            aarch64 | arm64) arch=arm64 ;;
            *) arch=x64 ;;
        esac
        gradle :desktopApp:createDistributable :desktopApp:packageDeb
        stage=$(mktemp -d)
        mkdir -p "$stage/licenses"
        cp -R "$binaries/app/Downstep" "$stage/Downstep"
        cp "$stage"/Downstep/lib/app/resources/licenses/* "$stage/licenses/"
        tar -czf "$dist/Downstep-linux-$arch.tar.gz" -C "$stage" Downstep licenses
        rm -rf "$stage"
        rm -f "$dist/Downstep-linux-$arch.deb"
        cp "$binaries"/deb/*.deb "$dist/Downstep-linux-$arch.deb"
        ;;
    *)
        echo "No package for $(uname -s)." >&2
        exit 1
        ;;
esac
ls -l "$dist"