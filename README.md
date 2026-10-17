# Downstep

Slow a song down, change its key and loop the part you are learning. Speed never changes the
pitch, and pitch never changes the speed. For Mac and Windows, free and open source.

Downloads are on the [Releases](../../releases) page.

## Features

- Speed from 25% to 200% and pitch up to 12 semitones up or down, independent of each other.
- A-B loop: set A and B while you listen or drag the markers; halve, double or move the loop.
- Library that works offline: MP3, M4A, WAV and FLAC files, or a YouTube link (audio only).
  Playlists, and presets for each song.
- Bell-curve equalizer: drag each band (frequency, gain, width) on the frequency graph, with preamp, saved per track; left and right balance.
- MP3 export with your changes applied: the full track, one file per loop, or both.
- Backup of the whole library in one `.sldbackup` file, restored on Mac or Windows.
- Keyboard: Space to play and stop, arrows to move, A, B and L for the loop.

Only import content you have the rights to. Downloading from YouTube can be against its terms of
service.

## Build

Requirements on every system: JDK 23 (the desktop modules use FFM), CMake 3.30 or later, and the
Android SDK (the shared modules also have an Android target, so Gradle needs it to configure). Set
`JAVA_HOME` and `ANDROID_HOME`, or put the SDK path in `local.properties`.

- macOS: the Xcode command line tools.
- Windows: MinGW-w64 GCC and Ninja, for example from MSYS2 (`mingw-w64-ucrt-x86_64-gcc`,
  `-cmake`, `-ninja`), as in the release workflow. Bungee does not build with MSVC. The scripts find
  them on `PATH`, in `C:\msys64\ucrt64\bin` and in a few other usual places.
  The engine's CMake build checks out Bungee and Eigen with git, which a deep folder takes past
  Windows' path limit: the scripts turn on git's `core.longpaths` for it. Running Gradle or CMake
  directly from a deep folder needs `git config --global core.longpaths true`.
- Linux: GCC or Clang, and `fakeroot` and `dpkg` for the `.deb`.
- For the Docker targets: Docker only. The image has everything else.

The tasks run with GNU make (Linux, macOS and Windows) or with [just](https://github.com/casey/just).
Both call the same scripts: PowerShell in `scripts/windows`, sh in `scripts/linux` (also used on
macOS). On Windows nothing needs bash, Git Bash or WSL: make and just run PowerShell 7, or Windows
PowerShell when 7 is missing (make only).

```sh
make help     # or: just --list
make test     # or: just test
```

| Target | What it does |
| --- | --- |
| `build` | compile the desktop app and the native engine |
| `test` | the Kotlin tests of every module |
| `engine` | the native engine, in `engine-desktop/build/native` |
| `engine-test` | the engine's C++ tests |
| `package` | the app in `build/dist`: zip on Windows, `.tar.gz` and `.deb` on Linux, DMG on macOS |
| `run` | start the app |
| `clean` | remove the build outputs |
| `docker-image` | build the Linux build image (`docker/Dockerfile`) |
| `docker-build`, `docker-test`, `docker-engine`, `docker-engine-test`, `docker-package` | the same targets in the Linux container |
| `docker-shell` | a shell in the container |
| `docker-clean` | remove the container's volumes and `build/docker-out` |

`APP_VERSION=1.2.3` sets the version of a package, as in the release workflow.

The Docker targets build the image if needed and run the Linux build from Windows, macOS or Linux.
The container gets the repository read-only and copies the sources into its own volume, with LF line
endings, so its build never mixes with the host's (the engine's CMake folder, for one). Gradle's caches
stay in a second volume, so the second run is much faster. Test reports, the engine and the packages
come back to `build/docker-out`.

Gradle directly works as well:

```sh
./gradlew :desktopApp:run                 # start the app
./gradlew :desktopApp:packageDmg          # unsigned DMG (macOS)
./gradlew :desktopApp:packageDeb          # .deb (Linux)
./gradlew :desktopApp:createDistributable # app folder
./gradlew :shared:jvmTest :shared-data:desktopTest :shared-ui:desktopTest
./gradlew :engine-desktop:test :desktopApp:test
```

The app keeps its data in `~/Library/Application Support/SlowDowner` on macOS, in
`%APPDATA%\SlowDowner` on Windows and in `~/.local/share/SlowDowner` on Linux. The folder has the
app's first name.

## How it works

Kotlin Multiplatform with Compose Multiplatform. Screens, ViewModels, database and use-cases are
shared; only the audio engine and a few platform services are different.

```
shared/          domain models and use-cases, pure Kotlin
shared-data/     Room, repositories, YouTube import (NewPipe Extractor), backups
shared-ui/       Compose Multiplatform: screens, ViewModels, theme, strings
engine/          C++ audio engine: Bungee time-stretch, EQ, miniaudio output, LAME export
engine-desktop/  Kotlin binding of the engine through FFM (java.lang.foreign)
desktopApp/      Compose Desktop app for macOS and Windows
website/         the site, a static page generator (python3 website/build.py, no dependencies)
```

## About this repository

I develop Downstep in a private repository, together with an Android version that I can't
publish. This repository is a mirror of the parts I can share, and it updates by itself.

Issues are welcome. Pull requests can't be merged here directly, because the mirror would
overwrite them: if you send one, I will bring the change over by hand.

## License

GPL-3.0, see [LICENSE](LICENSE). Third-party components and their licenses are listed in
[desktopApp/licenses/THIRD-PARTY-NOTICES.txt](desktopApp/licenses/THIRD-PARTY-NOTICES.txt),
next to the full license texts. The app carries the same folder.
