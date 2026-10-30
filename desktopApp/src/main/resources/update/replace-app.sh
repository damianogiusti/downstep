#!/bin/sh
# Started by Downstep before it quits to update itself. Waits for it to quit, puts the new app in
# place of the old one, and opens it. The old app goes to the Trash: nothing is deleted for good.
# Usage: replace-app.sh <pid> <new app> <installed app> [folder that stands in for the Trash, in tests]
# Next to this script it leaves swap-result, one word the next start reports (see UpdateSwapFiles).
pid="$1"
new="$2"
target="$3"
trash_dir="${4:-}"
# From the installed name, as on Windows: an app left as "Downstep (old).app" and opened from there
# must not trash itself at the next update.
old="${target%.app} (old).app"
result_file="$(dirname "$0")/swap-result"

# Before the app opens: it reads the word at its start.
result() {
    echo "$1" > "$result_file"
}

# The system call behind Finder's "Move to Trash": a rename into the Trash, no permission prompt.
to_trash() {
    [ -e "$1" ] || return 0
    if [ -n "$trash_dir" ]; then
        mv "$1" "$trash_dir/"
        return
    fi
    osascript -l JavaScript -e '
        function run(argv) {
            ObjC.import("Foundation");
            var url = $.NSURL.fileURLWithPath(argv[0]);
            if (!$.NSFileManager.defaultManager.trashItemAtURLResultingItemURLError(url, null, null)) {
                throw new Error("Not moved to the Trash");
            }
        }' "$1"
}

echo "$(date): waiting for $pid"
i=0
while kill -0 "$pid" 2>/dev/null; do
    i=$((i + 1))
    if [ "$i" -gt 240 ]; then
        echo "The app did not quit: nothing changed"
        result app-did-not-quit
        exit 1
    fi
    sleep 0.5
done

# Left by an update that stopped halfway.
if ! to_trash "$old"; then
    echo "Cannot move $old to the Trash: nothing changed"
    result old-copy-stuck
    open "$target"
    exit 1
fi
if ! mv "$target" "$old"; then
    echo "Cannot move the installed app: nothing changed"
    result installed-app-in-use
    open "$target"
    exit 1
fi
# A rename when the app is on the same disk as the user data, as usual; mv copies otherwise.
replaced=false
app="$target"
if mv "$new" "$target"; then
    echo "Replaced"
    result replaced
    replaced=true
else
    echo "Cannot move the new app: the old one goes back"
    result new-app-not-moved
    # A copy to another disk can stop halfway: that piece of the new app goes to the Trash too.
    to_trash "$target"
    # Only into an empty place: a piece left behind would swallow the old app inside it.
    if [ -e "$target" ] || ! mv "$old" "$target"; then
        echo "The old app stays as $old"
        app="$old"
    fi
fi
# Written by the app, the new files have no quarantine mark: this only makes sure.
xattr -dr com.apple.quarantine "$app" 2>/dev/null
open "$app" && echo "Started"
# After the app opens: emptying the old one into the Trash can take a moment.
if [ "$replaced" = true ]; then
    to_trash "$old" && echo "Old app in the Trash" || echo "The old app stays as $old"
fi
