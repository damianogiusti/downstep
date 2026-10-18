#!/bin/sh
# Rebuilds the packaging icons from the SVGs: downstep.ico (Windows) and downstep.png (Linux) from
# downstep.svg, downstep.icns (macOS) from downstep-macos.svg. Needs resvg (cargo install resvg, brew
# install resvg or apt install resvg) and python3; the .icns needs iconutil, so macOS. On Windows:
# make-icons.ps1.
set -eu
cd "$(dirname "$0")"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
render() { resvg -w "$2" -h "$2" "$1" "$3"; }

# Windows: the sizes Explorer, the taskbar and Alt+Tab ask for, from 100 to 200 % scaling.
for s in 16 20 24 32 40 48 64 128 256; do
  render downstep.svg "$s" "$work/ico_$s.png"
done
python3 -I make-ico.py downstep.ico "$work"/ico_*.png
render downstep.svg 512 downstep.png

if command -v iconutil > /dev/null; then
  mkdir "$work/downstep.iconset"
  for s in 16 32 128 256 512; do
    render downstep-macos.svg "$s" "$work/downstep.iconset/icon_${s}x${s}.png"
    render downstep-macos.svg "$((s * 2))" "$work/downstep.iconset/icon_${s}x${s}@2x.png"
  done
  iconutil -c icns "$work/downstep.iconset" -o downstep.icns
else
  echo "No iconutil (macOS only): downstep.icns left as it is." >&2
fi
