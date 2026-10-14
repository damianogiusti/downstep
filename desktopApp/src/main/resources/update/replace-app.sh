#!/bin/sh
# Started by Downstep before it quits to update itself. Waits for it to quit, puts the new app in
# place of the old one, and opens it. Usage: replace-app.sh <pid> <new app> <installed app>
pid="$1"
new="$2"
target="$3"
backup="$(dirname "$target")/.$(basename "$target").old"

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

rm -rf "$backup"
if ! mv "$target" "$backup"; then
    echo "Cannot move the installed app: nothing changed"
    open "$target"
    exit 1
fi
# A rename when the app is on the same disk as the user data, as usual; mv copies otherwise.
if mv "$new" "$target"; then
    rm -rf "$backup"
    echo "Replaced"
else
    echo "Cannot move the new app: the old one goes back"
    rm -rf "$target"
    mv "$backup" "$target"
fi
# Written by the app, the new files have no quarantine mark: this only makes sure.
xattr -dr com.apple.quarantine "$target" 2>/dev/null
open "$target" && echo "Started"
