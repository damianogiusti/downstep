# syntax=docker/dockerfile:1
# The Linux build image: everything the README asks for, at fixed versions. The sources are not in
# the image: scripts/windows/docker.ps1 and scripts/linux/docker.sh mount them at /src and run
# container-run.sh, which builds in the container's own copy (see there).
FROM eclipse-temurin:23.0.2_7-jdk-noble

# CMake from Kitware: the engine needs 3.30 or later, Ubuntu 24.04 has 3.28.
ARG CMAKE_VERSION=4.1.2
# Android command line tools 19.0. The shared modules have an Android target (compileSdk 37), so
# Gradle needs the SDK to configure, even for desktop tasks.
ARG ANDROID_CMDLINE_TOOLS=13114758
# compileSdk 37 resolves to the android-37.1 platform (what AGP downloads on a fresh machine).
ARG ANDROID_PACKAGES="platform-tools platforms;android-37.1 build-tools;37.0.0"

ENV DEBIAN_FRONTEND=noninteractive
# The engine: GCC, Ninja, git for CMake's FetchContent. Packaging: jpackage needs binutils (objcopy),
# dpkg and fakeroot for the .deb. Compose tests and the app: X11, GL and fontconfig for Skiko; ALSA
# and PulseAudio for miniaudio, which loads them at run time. xvfb for the UI tests without a screen.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        binutils ca-certificates curl dpkg-dev fakeroot file g++ gcc git make ninja-build rsync unzip xz-utils \
        libasound2t64 libpulse0 libfontconfig1 libfreetype6 libgl1 libx11-6 libxext6 libxi6 libxrender1 libxtst6 \
        xauth xvfb \
    && rm -rf /var/lib/apt/lists/*

RUN arch="$(uname -m)" \
    && curl -fsSL "https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-linux-${arch}.tar.gz" \
        | tar -xz -C /opt \
    && ln -s "/opt/cmake-${CMAKE_VERSION}-linux-${arch}" /opt/cmake \
    && /opt/cmake/bin/cmake --version
ENV PATH=/opt/cmake/bin:$PATH

ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
    && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install $ANDROID_PACKAGES > /dev/null \
    && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --list_installed
ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# The same choices as the Windows job of release.yml, and a Gradle without daemon: every run is a
# new container. The caches live in a volume at GRADLE_USER_HOME.
ENV CMAKE_GENERATOR=Ninja \
    CC=gcc \
    CXX=g++ \
    GRADLE_USER_HOME=/home/builder/.gradle \
    GRADLE_OPTS=-Dorg.gradle.daemon=false

# Ubuntu's own user has uid 1000: builder takes its place. The folders are open to every uid, so a
# Linux host can run the container as itself (docker.sh does) and own what lands in build/docker-out.
RUN (userdel --remove ubuntu 2> /dev/null || true) \
    && useradd --create-home --uid 1000 --shell /bin/bash builder \
    && mkdir -p /home/builder/.gradle /work /out \
    && chmod 0777 /home/builder /home/builder/.gradle /work /out

# CRLF from a Windows checkout would break sh: the script is stored with LF whatever the host has.
COPY docker/container-run.sh /usr/local/bin/container-run
RUN sed -i 's/\r$//' /usr/local/bin/container-run && chmod 0755 /usr/local/bin/container-run

USER builder
ENV HOME=/home/builder
WORKDIR /work
ENTRYPOINT ["/usr/local/bin/container-run"]
CMD ["help"]
