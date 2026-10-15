#!/bin/sh
# Started by Downstep before it quits to update itself. Waits for it to quit, puts the new app in
# place of the old one, and opens it. The old app goes to the Trash: nothing is deleted for good.
# Usage: replace-app.sh <pid> <new app> <installed app> [folder that stands in for the Trash, in tests]
pid="$1"
new="$2"
target="$3"
trash_dir="${4:-}"
old="$(dirname "$target")/Downstep (old).app"

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
        exit 1
    fi
    sleep 0.5
done

# Left by an update that stopped halfway.
if ! to_trash "$old"; then
    echo "Cannot move $old to the Trash: nothing changed"
    open "$target"
    exit 1
fi
if ! mv "$target" "$old"; then
    echo "Cannot move the installed app: nothing changed"
    open "$target"
    exit 1
fi
# A rename when the app is on the same disk as the user data, as usual; mv copies otherwise.
if mv "$new" "$target"; then
    echo "Replaced"
    to_trash "$old" && echo "Old app in the Trash" || echo "The old app stays as $old"
else
    echo "Cannot move the new app: the old one goes back"
    # A copy to another disk can stop halfway: that piece of the new app goes to the Trash too.
    to_trash "$target"
    mv "$old" "$target"
fi
# Written by the app, the new files have no quarantine mark: this only makes sure.
xattr -dr com.apple.quarantine "$target" 2>/dev/null
open "$target" && echo "Started"
