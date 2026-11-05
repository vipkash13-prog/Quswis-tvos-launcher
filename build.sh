#!/bin/bash
# Builds and signs build/tvos-home.apk with plain SDK tools (no Gradle).
set -euo pipefail
cd "$(dirname "$0")"

# JDK 17+ and the Android SDK (build-tools 34.0.0, platform android-34). Override with the usual
# variables: JAVA_HOME, ANDROID_HOME (or ANDROID_SDK_ROOT).
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/.local/jdk-17.0.20.1+1/Contents/Home" ]; then
    JAVA_HOME="$HOME/.local/jdk-17.0.20.1+1/Contents/Home"
fi
if [ -n "${JAVA_HOME:-}" ]; then export JAVA_HOME; export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
[ -d "$SDK" ] || SDK="$HOME/Android/Sdk"  # Linux default
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
for f in "$BT/aapt2" "$BT/d8" "$JAR"; do
    [ -e "$f" ] || { echo "Missing $f — install build-tools 34.0.0 and platform android-34 (sdkmanager), or set ANDROID_HOME." >&2; exit 1; }
done
command -v javac >/dev/null || { echo "javac not found — install JDK 17+ or set JAVA_HOME." >&2; exit 1; }

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
    -A assets --java build/gen --min-sdk-version 24 --target-sdk-version 34 \
    --version-code "${VERSION_CODE:-5}" --version-name "${VERSION_NAME:-2.0.3}" build/res.zip

# The launcher uses Media3 ExoPlayer for Quran radio playback. This project intentionally
# builds without Gradle, so fetch the matching AAR class jars explicitly for javac and D8.
DEPS="$PWD/build/deps"
mkdir -p "$DEPS"
MEDIA3_VERSION="1.11.1"
for artifact in media3-common media3-container media3-database media3-decoder media3-datasource media3-extractor media3-exoplayer; do
    aar="$DEPS/$artifact.aar"
    curl --fail --silent --show-error --location \
        "https://dl.google.com/dl/android/maven2/androidx/media3/$artifact/$MEDIA3_VERSION/$artifact-$MEDIA3_VERSION.aar" \
        -o "$aar"
    unzip -p "$aar" classes.jar > "$DEPS/$artifact.jar"
done
# Media3 exposes AndroidX annotation types in its public signatures.
curl --fail --silent --show-error --location \
    "https://dl.google.com/dl/android/maven2/androidx/annotation/annotation-jvm/1.9.1/annotation-jvm-1.9.1.jar" \
    -o "$DEPS/annotation.jar"
# Guava's Android-compatible artifact supplies ImmutableList in Media3 public APIs.
curl --fail --silent --show-error --location \
    "https://repo.maven.apache.org/maven2/com/google/guava/guava/33.3.1-android/guava-33.3.1-android.jar" \
    -o "$DEPS/guava.jar"
curl --fail --silent --show-error --location \
    "https://repo.maven.apache.org/maven2/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar" \
    -o "$DEPS/failureaccess.jar"
curl --fail --silent --show-error --location \
    "https://repo.maven.apache.org/maven2/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict/listenablefuture-9999.0-empty-to-avoid-conflict.jar" \
    -o "$DEPS/listenablefuture.jar"
CLASSPATH="$JAR"
for dependency in "$DEPS"/*.jar; do CLASSPATH="$CLASSPATH:$dependency"; done

javac -encoding UTF-8 --release 11 -classpath "$CLASSPATH" \
    -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --classpath "$CLASSPATH" --output build/dex \
    $(find build/classes -name '*.class') "$DEPS"/*.jar
(cd build/dex && zip -q ../unsigned.apk classes.dex)

"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# Signing. Release builds: RELEASE_KEYSTORE + RELEASE_KEYSTORE_PASS (the private key lives in the
# separate, private tvOS-Launcher-signing repo). Development builds: a local debug key, created on
# first use and never committed (it's in .gitignore).
if [ -n "${RELEASE_KEYSTORE:-}" ]; then
    [ -n "${RELEASE_KEYSTORE_PASS:-}" ] || { echo "Set RELEASE_KEYSTORE_PASS for $RELEASE_KEYSTORE" >&2; exit 1; }
    "$BT/apksigner" sign --ks "$RELEASE_KEYSTORE" --ks-pass env:RELEASE_KEYSTORE_PASS \
        --ks-key-alias "${RELEASE_KEY_ALIAS:-tvos-release}" --out build/tvos-home.apk build/aligned.apk
    echo "Signed with the release key"
else
    KS=debug.keystore
    if [ ! -f "$KS" ]; then
        keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias debug \
            -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvOS Launcher debug" >/dev/null
    fi
    "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
        --out build/tvos-home.apk build/aligned.apk
    echo "Signed with the local debug key (development build)"
fi

echo "Built build/tvos-home.apk"
