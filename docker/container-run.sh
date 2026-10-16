#!/bin/sh
# Runs one task inside the build container (the image's entry point): container-run <target>.
#
# The host's checkout is mounted read-only at /src. Building there would mix the host's outputs
# with the container's (engine-desktop/build/native holds a CMake cache for the host's compiler) and
# a Windows checkout has CRLF line endings, which sh does not read. So the sources are copied into
# the container's own tree in the work volume, with LF endings, and the build runs there. Only what
# the task produces goes back to the host, in /out (build/docker-out on the host).
set -eu

target=${1:-help}
src=/src
mirror=/work/mirror
tree=/work/tree
out=/out

usage() {
    cat <<'EOF'
Usage: container-run <target>

  build        compile the desktop app and the engine
  test         Kotlin tests of every module (reports in build/docker-out/reports)
  engine       the native engine, libsdengine.so (copied to build/docker-out/engine)
  engine-test  the engine's C++ tests
  package      app folder (.tar.gz) and .deb in build/docker-out/dist
  shell        a shell in the container's copy of the sources
EOF
}

# The mirror is an exact copy of /src without the outputs (rsync only copies what changed), and
# rsync reports every change. The tree gets the same changes with LF endings. Normalising the
# mirror itself would make rsync copy every CRLF file again on each run, and touch it for CMake.
sync_sources() {
    if [ ! -d "$src" ] || [ -z "$(ls -A "$src")" ]; then
        echo "No sources at $src: mount the repository there (see scripts/linux/docker.sh)." >&2
        exit 2
    fi
    mkdir -p "$mirror" "$tree"
    changes=$(mktemp)
    rsync -a --delete --out-format='%i|%n' \
        --exclude='/.git' --exclude='/.claude/' --exclude='/.handoff-inputs/' --exclude='/dist/' \
        --exclude='build/' --exclude='.gradle/' --exclude='.kotlin/' --exclude='.idea/' --exclude='*.iml' \
        --exclude='local.properties' --exclude='.DS_Store' \
        "$src/" "$mirror/" > "$changes"
    count=0
    while IFS= read -r line; do
        item=${line%%|*}
        path=${line#*|}
        case $item in
            '*deleting'*) rm -rf "${tree:?}/$path" ;;
            cd*) mkdir -p "$tree/$path" ;;
            cL*) rm -rf "${tree:?}/$path"; cp -P "$mirror/$path" "$tree/$path" ;;
            ?f*)
                mkdir -p "$(dirname "$tree/$path")"
                cp -p "$mirror/$path" "$tree/$path"
                # Text files only (grep -I skips binaries), and the source's time, so CMake and
                # Gradle see the file as changed only when it did.
                if grep -q "$(printf '\r')" "$tree/$path" && grep -Iq . "$tree/$path"; then
                    sed -i 's/\r$//' "$tree/$path"
                    touch -r "$mirror/$path" "$tree/$path"
                fi
                count=$((count + 1))
                ;;
        esac
    done < "$changes"
    rm -f "$changes"
    echo "Sources in sync: $count files updated."
}

# Test results and reports of every module, also when a test failed.
collect_reports() {
    rm -rf "$out/reports"
    for dir in "$tree"/*/build/reports "$tree"/*/build/test-results; do
        [ -d "$dir" ] || continue
        module=$(basename "$(dirname "$(dirname "$dir")")")
        mkdir -p "$out/reports/$module"
        cp -R "$dir" "$out/reports/$module/"
    done
    if [ -d "$out/reports" ]; then echo "Reports: build/docker-out/reports"; fi
}

case $target in
    help | -h | --help) usage; exit 0 ;;
    build | test | engine | engine-test | package | shell) ;;
    *) echo "Unknown target: $target" >&2; usage >&2; exit 2 ;;
esac

sync_sources
cd "$tree"
status=0
case $target in
    shell)
        exec bash
        ;;
    test)
        sh scripts/linux/test.sh || status=$?
        collect_reports
        ;;
    engine)
        sh scripts/linux/engine.sh || status=$?
        if [ "$status" -eq 0 ]; then
            rm -rf "$out/engine" && mkdir -p "$out/engine"
            cp engine-desktop/build/native/libsdengine.so "$out/engine/"
            echo "Engine: build/docker-out/engine/libsdengine.so"
        fi
        ;;
    package)
        sh scripts/linux/package.sh || status=$?
        if [ "$status" -eq 0 ]; then
            rm -rf "$out/dist" && mkdir -p "$out/dist"
            cp -R build/dist/. "$out/dist/"
            echo "Packages: build/docker-out/dist"
        fi
        ;;
    *)
        sh "scripts/linux/$target.sh" || status=$?
        ;;
esac
exit "$status"
