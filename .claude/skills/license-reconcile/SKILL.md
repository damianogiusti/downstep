---
name: license-reconcile
description: Reconcile the licenses of everything Downstep ships (desktop app, website) with the notices and license texts we distribute. Use when a dependency is added, removed or bumped, before a release, when touching engine/CMakeLists.txt, desktopApp packaging or the release workflows, or when asked about licenses, GPL, open source obligations, NOTICE or third-party notices.
---

# License reconciliation

Goal: every component that ends up in a distributed artifact has a known license, the licenses are
compatible with **GPL-3.0** (Downstep's outbound license), and every obligation is met **in every
artifact**. Output a report first; change files only after the user agrees.

Not a lawyer: flag doubts, never resolve them by guessing.

## Why GPL-3.0 (do not re-litigate unless asked)

NewPipeExtractor is GPL-3.0-or-later and runs in the app's process, so the distributed app is a
combined work under the GPL. Bungee/Eigen/Rhino are MPL-2.0 (file-level copyleft, no Exhibit B), LAME
is LGPL v2+, everything else is permissive. If NewPipe ever leaves the desktop build, say that the
outbound license becomes a free choice again, and that LAME is then statically linked on macOS
(LGPL relinking issue for a closed app).

## Scope

| Shipped artifact | Built by | Licenses must be in |
|---|---|---|
| macOS DMG / `Downstep.app` | `:desktopApp:packageDmg`, `.github/workflows/release.yml` (job `mac`) | `Contents/app/resources/licenses/` |
| Windows zip | `.github/workflows/windows.yml`, `.github/workflows/release.yml` (job `windows`) | `Downstep/app/resources/licenses/` and the top-level `licenses` folder |
| This repository | git | root `LICENSE`, `desktopApp/licenses/` |
| Website (statichost.eu) | `website/build.py` | `website/static/assets/fonts/OFL.txt` beside the fonts; a source link next to the download |

Out of scope: iOS (no app), test-only and build-only dependencies (JUnit, KSP, Room compiler,
cxxopts).

Single source of truth: **`desktopApp/licenses/`** (committed). `syncLicenses` in
`desktopApp/build.gradle.kts` copies it, plus `shared-ui/FONTS-OFL.txt`, into Compose's `common`
app resources. The CI zips copy the folder from the built app. Never add a second notices file.

## Steps

### 1. Inventory

```bash
python3 -I .claude/skills/license-reconcile/scripts/jvm_licenses.py .     # JVM runtime classpath of :desktopApp
./gradlew :engine-desktop:buildNativeEngine                              # only if _deps is missing
python3 -I .claude/skills/license-reconcile/scripts/native_licenses.py .  # FetchContent sources of the engine
```

- `jvm_licenses.py` resolves `:desktopApp:runtimeClasspath`, reads POMs (following `<parent>`) and lists
  LICENSE/NOTICE files inside each jar. `UNKNOWN` means: check upstream source headers and the repo
  LICENSE (`gh api repos/<owner>/<repo>/license`), then add the module to `KNOWN` in the script.
- `native_licenses.py` reports license files, SPDX ids, MPL Exhibit B, and whether LAME's GPL-only
  `mpglib` is compiled (`HAVE_MPGLIB` in `engine/third_party/lame/config.h` must stay undefined).
  Only what the engine links counts: for Bungee, `bungee_library` and `pffft` (see its CMakeLists).
- Native code **inside jars**: the scripts do not see it. Check `skiko-awt-runtime-*` (Skia, ICU,
  HarfBuzz, libpng, zlib, libjpeg-turbo, Expat: `strings` on the dylib/dll) and any new jar with
  `.so/.dylib/.dll` entries. The resolved classpath is for the current OS: Windows uses
  `skiko-awt-runtime-windows-x64`, same licenses.
- Assets: fonts in `shared-ui/src/commonMain/composeResources/font` and `website/static/assets/fonts`
  (OFL), icons (Material, Apache-2.0), images made by us.
- The bundled Java runtime ships its own `legal/` folder: check it is still in the bundle.
- Libraries nested inside a dependency (Skia's third_party in Skiko, BSD/Boost code in the Kotlin
  stdlib) are named with a link to upstream, not reproduced verbatim: the user chose so on 2026-10-14.
  Report new ones the same way; do not propose copying their texts.

### 2. Classify and check compatibility

For each component: license, copyleft level, compatible with GPL-3.0? Known rules:
- Apache-2.0: OK with GPL-3.0 (not with GPL-2.0-only). Ship the license text and the upstream
  **NOTICE** file verbatim when one exists (Skiko has one; look in the upstream repo root, not only the jar).
- MPL-2.0: OK unless a file carries Exhibit B. Tell recipients where the source is.
- LGPL v2+: OK. Ship the text. Static linking (macOS) is fine only because the whole source is public.
- MIT / BSD / FFTPACK / ICU / zlib-style: reproduce copyright + permission text in the notices.
- GPL-2.0-only, AGPL, SSPL, "non-commercial", no license at all: **stop and report**.

### 3. Compare with what we ship

- Every non-Apache component in the inventory has an entry in `THIRD-PARTY-NOTICES.txt`, with the
  same version; every Apache group is named. Removed dependencies are removed from the file.
- Every license family in the inventory has its full text in `desktopApp/licenses/`.
- Verbatim blocks (MIT/BSD texts, NOTICE files) match upstream at the shipped version.
- Build and look inside the artifacts: `./gradlew :desktopApp:createDistributable`, then
  `ls desktopApp/build/compose/binaries/main/app/Downstep.app/Contents/app/resources/licenses`.
  Windows: read the zip step in both workflows (no local build).
- Website: fonts license next to the fonts; the download page has a clear link to the source
  (GPL-3.0 section 6d asks for directions next to the object code).
- Repository: `LICENSE` is the GPL-3.0 text; the README points to `desktopApp/licenses/`.

### 4. Report

Reply in the user's language, short:
1. Verdict: outbound license still valid? Any blocker?
2. Table of changes since the last notices (added/removed/bumped components, license changes).
3. Gaps, per artifact, each with the concrete fix.
4. Residual risks you could not verify.

### 5. Apply (after the user agrees)

- **Never write a license text or a copyright line from memory.** Copy it from the artifact (jar
  `META-INF`, `_deps/<name>-src`) or upstream at the shipped tag (`gh api repos/<o>/<r>/contents/<f>?ref=<tag>`).
- Keep `THIRD-PARTY-NOTICES.txt` in English, grouped as today: copyleft, Apache-2.0, other permissive.
  Indent verbatim blocks by 4 spaces.
- Run `scripts/leak-check.sh` before you push (see CONTRIBUTING.md).
- Rebuild and re-check the bundle (step 3). Do not commit unless asked.
