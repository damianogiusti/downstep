#!/bin/sh
# make help: the targets of the Makefile, with the text after "##".
set -eu
cd "$(dirname "$0")/../.."
echo 'Targets (make <target> or just <target>):'
sed -n 's/^\([a-z-]*\):.*## \(.*\)$/\1|\2/p' Makefile | while IFS='|' read -r name text; do
    printf '  %-20s %s\n' "$name" "$text"
done