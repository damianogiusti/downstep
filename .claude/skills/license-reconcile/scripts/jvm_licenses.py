#!/usr/bin/env python3
"""Licenses of every JVM library that ships inside the desktop app.

Resolves :desktopApp's runtimeClasspath with Gradle, then reads each module's POM from the Gradle
cache, following <parent> when the POM declares no license (protobuf does this). It also lists
LICENSE/NOTICE files found inside the jar: Apache-2.0 asks to ship a NOTICE when there is one.

Usage: python3 -I jvm_licenses.py <repo root>
Output: one tab-separated line per module (coordinate, license, where it was found, files in jar).
"""
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
LINE = re.compile(r"--- ([^:\s]+):([^:\s]+):?(\S*)(?: -> (\S+))?")
# Modules whose POM declares no license, checked by hand against the upstream sources.
KNOWN = {
    "com.github.TeamNewPipe:nanojson": "Apache-2.0 (source headers; upstream mmastrac/nanojson is MIT or Apache-2.0)",
}


def resolved_modules(repo):
    out = subprocess.run(
        ["./gradlew", "-q", ":desktopApp:dependencies", "--configuration", "runtimeClasspath"],
        cwd=repo, capture_output=True, text=True, check=True).stdout
    modules = set()
    for line in out.splitlines():
        # Rich versions ("{strictly 1.0}", "{prefer 2.1.0}") are requests: the resolved one follows "->".
        m = LINE.search(re.sub(r":\{[^}]*\}", ":", line))
        if not m or line.rstrip().endswith("(c)"):
            continue
        group, name, version, forced = m.groups()
        version = forced or version
        if group == "project" or not version:
            continue
        modules.add((group, name, version))
    return sorted(modules)


def pom_path(group, name, version):
    hits = glob.glob(os.path.join(CACHE, group, name, version, "*", f"{name}-{version}.pom"))
    return hits[0] if hits else None


def strip_ns(root):
    for el in root.iter():
        el.tag = el.tag.split("}")[-1]
    return root


def licenses_of(group, name, version, depth=0):
    """Returns (licenses, where): where is the POM that declared them."""
    path = pom_path(group, name, version)
    if not path:
        return [], "no POM in cache"
    root = strip_ns(ET.parse(path).getroot())
    names = [l.findtext("name") or l.findtext("url") for l in root.findall("./licenses/license")]
    if names:
        return names, "pom" if depth == 0 else f"parent {group}:{name}:{version}"
    parent = root.find("parent")
    if parent is not None and depth < 5:
        return licenses_of(parent.findtext("groupId"), parent.findtext("artifactId"),
                           parent.findtext("version"), depth + 1)
    return [], "none declared"


def jar_legal_files(group, name, version):
    jars = [j for j in glob.glob(os.path.join(CACHE, group, name, version, "*", f"{name}-{version}*.jar"))
            if "-sources" not in j and "-javadoc" not in j]
    if not jars:
        return "-"
    with zipfile.ZipFile(jars[0]) as z:
        found = [n for n in z.namelist()
                 if re.search(r"(licen[cs]e|notice|copying)[^/]*$", n, re.I) and not n.endswith("/")]
    return " ".join(found) or "-"


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    for group, name, version in resolved_modules(repo):
        names, where = licenses_of(group, name, version)
        if not names and f"{group}:{name}" in KNOWN:
            names, where = [KNOWN[f"{group}:{name}"]], "KNOWN table in this script"
        print("\t".join([f"{group}:{name}:{version}", " | ".join(names) or "UNKNOWN", where,
                         jar_legal_files(group, name, version)]))


if __name__ == "__main__":
    main()
