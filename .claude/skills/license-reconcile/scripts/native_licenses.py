#!/usr/bin/env python3
"""Licenses of the C/C++ code fetched by engine/CMakeLists.txt.

Reads the FetchContent sources that the last engine build downloaded (_deps/<name>-src) and reports,
for each one: its license files, the SPDX ids found in source headers, whether any source file
applies MPL-2.0 Exhibit B ("Incompatible With Secondary Licenses", which would block the GPL), and,
for LAME, whether the GPL-only mpglib decoder is compiled in.

Usage: python3 -I native_licenses.py <repo root>
Run ./gradlew :engine-desktop:buildNativeEngine first if _deps is missing.
"""
import collections
import os
import re
import sys

SOURCE = re.compile(r"\.(c|cc|cpp|h|hpp)$")
LEGAL = re.compile(r"^(licen[cs]e|copying|notice|copyright)", re.I)
SPDX = re.compile(r"SPDX-License-Identifier:\s*([\w.\-+ ()]+)")
EXHIBIT_B = "Incompatible With Secondary Licenses"


def deps_dir(repo):
    for candidate in ("engine-desktop/build/native/_deps", "engine/build/_deps"):
        path = os.path.join(repo, candidate)
        if os.path.isdir(path):
            return path
    sys.exit("no _deps folder: run ./gradlew :engine-desktop:buildNativeEngine")


def scan(src):
    legal, spdx, exhibit_b = [], collections.Counter(), []
    for root, dirs, files in os.walk(src):
        dirs[:] = [d for d in dirs if d not in (".git", "test", "tests", "doc", "docs")]
        for f in files:
            path = os.path.join(root, f)
            rel = os.path.relpath(path, src)
            if LEGAL.match(f):
                legal.append(rel)
            elif SOURCE.search(f):
                with open(path, errors="ignore") as fh:
                    head = fh.read(4000)
                for m in SPDX.finditer(head):
                    spdx[m.group(1).strip()] += 1
                if EXHIBIT_B in head:
                    exhibit_b.append(rel)
    return legal, spdx, exhibit_b


def lame_mpglib(repo):
    config = os.path.join(repo, "engine/third_party/lame/config.h")
    with open(config) as fh:
        defined = re.search(r"^\s*#\s*define\s+HAVE_MPGLIB\b", fh.read(), re.M)
    return "mpglib (GPL) COMPILED IN" if defined else "mpglib not compiled (HAVE_MPGLIB undefined)"


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    deps = deps_dir(repo)
    print(f"# {deps}")
    for entry in sorted(os.listdir(deps)):
        if not entry.endswith("-src"):
            continue
        legal, spdx, exhibit_b = scan(os.path.join(deps, entry))
        print(f"\n== {entry[:-4]}")
        print("license files:", ", ".join(sorted(legal)) or "NONE")
        print("SPDX in sources:", dict(spdx) or "none")
        print("Exhibit B applied in:", ", ".join(exhibit_b) or "no file")
        if entry.startswith("lame"):
            print("LAME:", lame_mpglib(repo))


if __name__ == "__main__":
    main()
