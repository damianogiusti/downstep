#!/bin/sh
# Rebuilds SlowDowner.icns (macOS) and SlowDowner.ico (Windows) from SlowDowner.svg. Needs macOS 14+ (AppKit reads SVG) and Xcode's swiftc.
set -e
cd "$(dirname "$0")"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
swiftc -O render.swift -o "$work/render"
mkdir "$work/SlowDowner.iconset"
for s in 16 32 128 256 512; do
  "$work/render" SlowDowner.svg "$s" "$work/SlowDowner.iconset/icon_${s}x${s}.png"
  "$work/render" SlowDowner.svg "$((s * 2))" "$work/SlowDowner.iconset/icon_${s}x${s}@2x.png"
done
iconutil -c icns "$work/SlowDowner.iconset" -o SlowDowner.icns
"$work/render" SlowDowner.svg 1024 SlowDowner.png

# Windows: the sizes Explorer and the taskbar ask for.
for s in 16 24 32 48 64 128 256; do
  "$work/render" SlowDowner.svg "$s" "$work/ico_$s.png"
done
python3 -I make-ico.py SlowDowner.ico "$work"/ico_*.png
