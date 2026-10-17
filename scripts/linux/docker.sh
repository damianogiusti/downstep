#!/bin/sh
# The Docker targets: docker.sh <image|build|test|engine|engine-test|package|shell|clean>.
# Builds the image (quick when nothing changed), then runs the target in a new container with the
# repository mounted read-only; see docker/container-run.sh. What the target produces lands in
# build/docker-out. Two volumes keep Gradle's caches (shared) and the container's build (one per
# checkout, named after its path) between runs.
set -eu

target=${1:-}
repo_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
image=${DOWNSTEP_DOCKER_IMAGE:-downstep-build}
gradle_volume=downstep-gradle
work_volume=downstep-work-$(printf '%s' "$repo_root" | cksum | cut -d ' ' -f 1)

case $target in
    image | build | test | engine | engine-test | package | shell) ;;
    clean)
        docker volume rm --force "$gradle_volume" "$work_volume"
        rm -rf "$repo_root/build/docker-out"
        exit 0
        ;;
    *)
        echo "Usage: $0 <image|build|test|engine|engine-test|package|shell|clean>" >&2
        exit 2
        ;;
esac

docker build --tag "$image" --file "$repo_root/docker/Dockerfile" "$repo_root"
[ "$target" = image ] && exit 0

mkdir -p "$repo_root/build/docker-out"
interactive=
[ "$target" = shell ] && interactive=-it
# As the host's user, so the files in build/docker-out are the host user's.
docker run --rm --init $interactive \
    --user "$(id -u):$(id -g)" \
    --mount "type=bind,source=$repo_root,target=/src,readonly" \
    --mount "type=bind,source=$repo_root/build/docker-out,target=/out" \
    --mount "type=volume,source=$gradle_volume,target=/home/builder/.gradle" \
    --mount "type=volume,source=$work_volume,target=/work" \
    --env APP_VERSION \
    "$image" "$target"