#!/usr/bin/env bash
# Fails if a denied term appears in the files or the commit messages that changed since BASE.
# Usage: scripts/leak-check.sh [terms-file] [base-ref]
# The terms file has one term per line (case-insensitive, fixed strings). It is not part of the
# repository. Default: ~/.config/downstep/denylist.txt. Default base: the upstream branch, or
# origin/main. The output names files and commits only, never the term.
set -euo pipefail

terms="${1:-$HOME/.config/downstep/denylist.txt}"
base="${2:-$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null || echo origin/main)}"

if [ ! -s "$terms" ]; then
  echo "No terms file at $terms: nothing to check."
  exit 0
fi

# Without empty lines, which would match everything.
clean="$(mktemp)"
trap 'rm -f "$clean"' EXIT
tr -d '\r' < "$terms" | grep -v '^[[:space:]]*$' > "$clean" || true
[ -s "$clean" ] || { echo "The terms file is empty: nothing to check."; exit 0; }

failed=0
while IFS= read -r file; do
  if printf '%s\n' "$file" | grep -iFq -f "$clean" || { [ -f "$file" ] && grep -iFq -f "$clean" -- "$file"; }; then
    echo "$file matches a denied term"
    failed=1
  fi
done < <(git diff --name-only --diff-filter=d "$base" HEAD)

for sha in $(git rev-list "$base..HEAD"); do
  if git log -1 --format='%an%n%ae%n%B' "$sha" | grep -iFq -f "$clean"; then
    echo "commit $sha message matches a denied term"
    failed=1
  fi
done

[ "$failed" -eq 0 ] && echo "No denied term found."
exit "$failed"
