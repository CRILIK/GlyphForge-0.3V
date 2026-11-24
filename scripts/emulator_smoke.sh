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
# Verify community header is below Android status bar and back is clickable.
adb shell am start -W -n com.example.glyphforge/.CommunityActivity
sleep 3
adb shell uiautomator dump /sdcard/community.xml >/dev/null || true
adb shell cat /sdcard/community.xml > emulator-artifacts/community.xml || true
adb exec-out screencap -p > emulator-artifacts/community.png
python3 - <<'PY'
import re, xml.etree.ElementTree as ET, subprocess
from pathlib import Path
xml=Path('emulator-artifacts/community.xml').read_text()
root=ET.fromstring(xml)
items=[n for n in root.iter('node') if 'Назад' in n.attrib.get('text','')]
assert items, 'Community Back button is missing from accessibility tree'
box=items[0].attrib.get('bounds','')
pts=[int(x) for x in re.findall(r'\\d+',box)]
assert len(pts)==4 and pts[1]>25 and pts[3]-pts[1]>40, f'Back button may be covered by status bar: {box}'
x=(pts[0]+pts[2])//2; y=(pts[1]+pts[3])//2
subprocess.run(['adb','shell','input','tap',str(x),str(y)],check=True)
print(f'PASS: Community Back button at {box}, tapped ({x},{y})')
PY
sleep 1
adb shell dumpsys activity activities | grep -Eq 'com.example.glyphforge/.MainActivity|com.example.glyphforge/com.example.glyphforge.MainActivity' || {
  echo 'Community back did not return to GlyphForge'; exit 1;
}
echo "PASS: community back action works and respects top system inset"
echo "PASS: APK installed, MainActivity launched, process alive, no fatal crash"
