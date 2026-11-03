#!/bin/bash
set -euo pipefail
SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
mkdir -p build/ui-classes build/ui-dex
javac --release 11 -cp "$JAR:build/classes" -d build/ui-classes tests/android/ViewportInstrumentation.java
"$BT/d8" --min-api 24 --lib "$JAR" --classpath build/classes --output build/ui-dex build/ui-classes/com/techgenius/tvoslauncher/ViewportInstrumentation.class
"$BT/aapt2" link -I "$JAR" --manifest tests/android/AndroidManifest.xml -o build/ui-test-unsigned.apk
(cd build/ui-dex && zip -q ../ui-test-unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/ui-test-unsigned.apk build/ui-test-aligned.apk
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --ks-key-alias debug --out build/ui-test.apk build/ui-test-aligned.apk
