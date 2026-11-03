#!/usr/bin/env bash
# Captures the demo-mode screenshot tour on a running emulator/device.
#
# Usage: scripts/capture-screenshots.sh [output-dir]
#
# Expects the debug app and its androidTest APK to be built already:
#   ./gradlew assembleDebug assembleDebugAndroidTest
# Produces <output-dir>/{login,dashboard,issue,history,detail,admin}-{light,dark}.png
set -euo pipefail

OUT_DIR="${1:-screenshots}"
APP_ID="com.srini.trafficfine.debug"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
TEST_CLASS="com.srini.trafficfine.screenshots.ScreenshotTour"

mkdir -p "$OUT_DIR"
adb wait-for-device

echo "Installing APKs"
adb install -r -t "$APP_APK"
adb install -r -t "$TEST_APK"

RUNNER="$(adb shell pm list instrumentation | tr -d '\r' | grep "target=$APP_ID)" | head -n1 | sed -E 's/^instrumentation:([^ ]+) .*/\1/' || true)"
if [ -z "$RUNNER" ]; then
  echo "No instrumentation found for $APP_ID" >&2
  adb shell pm list instrumentation >&2
  exit 1
fi
echo "Using runner $RUNNER"

# Clean, consistent status bar.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false

for VARIANT in light dark; do
  echo "Capturing $VARIANT theme"
  if [ "$VARIANT" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  adb shell pm clear "$APP_ID" > /dev/null
  sleep 2

  RESULT="$(adb shell am instrument -w -r -e class "$TEST_CLASS" -e variant "$VARIANT" "$RUNNER" | tr -d '\r')"
  echo "$RESULT" | tail -n 20
  if ! echo "$RESULT" | grep -q "^OK ("; then
    echo "Screenshot tour failed ($VARIANT)" >&2
    exit 1
  fi

  for NAME in login dashboard issue history detail admin; do
    FILE="$NAME-$VARIANT.png"
    adb exec-out run-as "$APP_ID" cat "files/screenshots/$FILE" > "$OUT_DIR/$FILE"
    if [ "$(head -c 4 "$OUT_DIR/$FILE" | tail -c 3)" != "PNG" ]; then
      echo "Missing or invalid $FILE" >&2
      exit 1
    fi
  done
done

adb shell cmd uimode night no
demo exit
ls -l "$OUT_DIR"
