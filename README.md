# Downstep

Slow a song down, change its key and loop the part you are learning. Speed never changes the
pitch, and pitch never changes the speed. For Mac and Windows, free and open source.

Downloads are on the [Releases](../../releases) page.

## Features

- Speed from 25% to 200% and pitch up to 12 semitones up or down, independent of each other.
- A-B loop: set A and B while you listen or drag the markers; halve, double or move the loop.
- Library that works offline: MP3, M4A, WAV and FLAC files, or a YouTube link (audio only).
  Playlists, and presets for each song.
- 7-band equalizer with preamp, left and right balance.
- MP3 export with your changes applied.
- Backup of the whole library in one `.sldbackup` file, restored on Mac or Windows.
- Keyboard: Space to play and stop, arrows to move, A, B and L for the loop.

Only import content you have the rights to. Downloading from YouTube can be against its terms of
service.

## Build

Requirements: JDK 23 (the desktop modules use FFM), CMake, the Android SDK (the shared modules also
have an Android target, so Gradle needs it to configure), and on macOS the Xcode command line tools.
On Windows the engine builds with MinGW-w64; Bungee does not build with MSVC.

```sh
./gradlew :desktopApp:run                 # start the app
./gradlew :desktopApp:packageDmg          # unsigned DMG (macOS)
./gradlew :desktopApp:createDistributable # app folder (macOS or Windows)
./gradlew :shared:jvmTest :shared-data:desktopTest :shared-ui:desktopTest
./gradlew :engine-desktop:test :desktopApp:test
```

The app keeps its data in `~/Library/Application Support/SlowDowner` on macOS and in
`%APPDATA%\SlowDowner` on Windows. The folder has the app's first name.

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
