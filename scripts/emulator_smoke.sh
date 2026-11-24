#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-artifacts
APK="$(find apk -name '*.apk' -print -quit)"
if [[ -z "$APK" ]]; then
  echo "APK artifact not found"; find apk -maxdepth 4 -type f -print; exit 1
fi
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n com.example.glyphforge/.MainActivity
sleep 8
PID="$(adb shell pidof com.example.glyphforge | tr -d '\r')"
if [[ -z "$PID" ]]; then
  adb logcat -d -b crash > emulator-artifacts/crash.log || true
  echo "GlyphForge process is not running"; cat emulator-artifacts/crash.log; exit 1
fi
echo "GlyphForge process: $PID"
adb shell uiautomator dump /sdcard/window.xml || true
adb shell cat /sdcard/window.xml > emulator-artifacts/window.xml || true
adb exec-out screencap -p > emulator-artifacts/launch.png
adb logcat -d -b crash > emulator-artifacts/crash.log || true
if grep -q 'FATAL EXCEPTION' emulator-artifacts/crash.log; then
  cat emulator-artifacts/crash.log; exit 1
fi
echo "PASS: APK installed, MainActivity launched, process alive, no fatal crash"
