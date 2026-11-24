#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-artifacts
APK="$(find apk -name '*.apk' -print -quit)"
if [[ -z "$APK" ]]; then
  echo "APK artifact not found"; find apk -maxdepth 4 -type f -print; exit 1
fi
adb install -r "$APK"
# Pixel Launcher on a cold-booted Google APIs emulator can display an ANR
# over the application under test. Stop only the emulator's home packages,
# never GlyphForge, before launch. A genuine GlyphForge ANR still fails QA.
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb shell am force-stop com.android.launcher3 || true
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
# The community activity is intentionally not exported. Navigate through
# the real app UI instead of trying to start it from adb shell.
tap_ui_label() {
  local snapshot="$1" needle="$2"
  local attempt=0
  while (( attempt < 10 )); do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null
    adb shell cat /sdcard/window.xml > "emulator-artifacts/$snapshot.xml"
    if python3 - "emulator-artifacts/$snapshot.xml" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET, subprocess
root=ET.parse(sys.argv[1]).getroot()
needle=sys.argv[2]
for n in root.iter('node'):
    label=(n.attrib.get('text','')+' '+n.attrib.get('content-desc','')).casefold()
    if needle.casefold() not in label or n.attrib.get('clickable')!='true':
        continue
    coords=[int(x) for x in re.findall(r'\d+',n.attrib.get('bounds',''))]
    if len(coords)!=4 or coords[2]-coords[0]<32 or coords[3]-coords[1]<32:
        continue  # Hidden WebView nodes can report [0,0][0,0].
    if needle=='Назад' and coords[1]<=25:
        raise AssertionError(f'Back button under status bar: {coords}')
    x=(coords[0]+coords[2])//2; y=(coords[1]+coords[3])//2
    subprocess.run(['adb','shell','input','tap',str(x),str(y)],check=True)
    print(f'PASS: tapped visible {needle!r} at {coords}')
    sys.exit(0)
print(f'NOT VISIBLE: {needle!r}',file=sys.stderr)
sys.exit(3)
PY
    then
      return 0
    fi
    # If the system launcher displays a delayed ANR, recover just that
    # process and bring the actual app forward. Do not dismiss app ANRs.
    if grep -Eiq "(Pixel Launcher|Launcher3).{0,80}(isn't responding|is not responding|keeps stopping)" "emulator-artifacts/$snapshot.xml"; then
      echo "Recovering unrelated emulator launcher ANR"
      adb shell am force-stop com.google.android.apps.nexuslauncher || true
      adb shell am force-stop com.android.launcher3 || true
      adb shell am start -W -n com.example.glyphforge/.MainActivity
      sleep 3
      (( attempt += 1 ))
      continue
    fi
    if [[ "$needle" == "Назад" ]]; then
      echo "Back button is not visible"; return 1
    fi
    adb shell input swipe 510 1650 510 610 400
    sleep 1
    (( attempt += 1 ))
  done
  echo "Failed to locate visible button: $needle"; return 1
}

tap_ui_label library-tab 'Библиотека'
sleep 2
tap_ui_label library-community 'Glyph Museum'
sleep 4
adb shell uiautomator dump /sdcard/community.xml >/dev/null || true
adb shell cat /sdcard/community.xml > emulator-artifacts/community.xml || true
adb exec-out screencap -p > emulator-artifacts/community.png
tap_ui_label community-back 'Назад'
sleep 2
adb shell uiautomator dump /sdcard/returned.xml >/dev/null
adb shell cat /sdcard/returned.xml > emulator-artifacts/returned.xml
python3 - <<'PY'
from pathlib import Path
s=Path('emulator-artifacts/returned.xml').read_text()
assert 'Библиотека' in s, 'Back button did not return to GlyphForge'
print('PASS: community back works and respects Android status bar')
PY
echo "PASS: APK installed, MainActivity launched, process alive, no fatal crash"
