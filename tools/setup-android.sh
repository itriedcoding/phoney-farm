#!/usr/bin/env bash
# Installs the Android build toolchain into $HOME/toolchain (no root assumptions).
set -uo pipefail
log() { echo "[setup] $*"; }

TC="$HOME/toolchain"
mkdir -p "$TC"
cd "$TC" || exit 1

SUDO=""
if [ "$(id -u)" = "0" ]; then SUDO=""; elif sudo -n true 2>/dev/null; then SUDO="sudo -n"; fi

# ---------------------------------------------------------------- JDK 17
if ! command -v java >/dev/null 2>&1; then
  if [ -n "$SUDO" ] || [ "$(id -u)" = "0" ]; then
    log "installing openjdk-17 via apt"
    $SUDO apt-get update -y >apt-update.log 2>&1
    $SUDO apt-get install -y openjdk-17-jdk-headless unzip curl >apt-jdk.log 2>&1 || true
  fi
fi
if ! command -v java >/dev/null 2>&1; then
  log "downloading Temurin 17"
  curl -fsSL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf jdk && mkdir -p jdk && tar xzf jdk.tar.gz -C jdk --strip-components=1
  export JAVA_HOME="$TC/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# ---------------------------------------------------------------- Android SDK
export ANDROID_HOME="$TC/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "downloading Android commandline tools"
  curl -fsSL -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf "$ANDROID_HOME" && mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
yes | "$SDKM" --sdk_root="$ANDROID_HOME" --licenses >sdk-licenses.log 2>&1
"$SDKM" --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-34" "build-tools;34.0.0" >sdk-install.log 2>&1
log "sdk installed: $(ls "$ANDROID_HOME/platforms")"

# ---------------------------------------------------------------- Gradle
if [ ! -x "$TC/gradle-8.7/bin/gradle" ]; then
  log "downloading Gradle 8.7"
  curl -fsSL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.7-bin.zip"
  unzip -q -o gradle.zip -d "$TC"
fi

{
  echo "export JAVA_HOME=$JAVA_HOME"
  echo "export PATH=$JAVA_HOME/bin:$TC/gradle-8.7/bin:\$PATH"
  echo "export ANDROID_HOME=$ANDROID_HOME"
  echo "export ANDROID_SDK_ROOT=$ANDROID_HOME"
} >"$TC/env.sh"

log "DONE java=$(java -version 2>&1 | head -1) gradle=$("$TC/gradle-8.7/bin/gradle" --version 2>/dev/null | grep '^Gradle ')"
