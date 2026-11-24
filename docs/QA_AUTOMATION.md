# Automated QA for GlyphForge

## Coverage
`tests_smoke.py` runs browser-driven tests in Chromium at mobile and desktop resolutions. It covers navigation, editing the 137 LED grid, frames, saving, importing two Running Kitten GIF versions and Glyph Museum JSON, library export/import, scenes, notification rules, music presets, audio-response visualization, PNG/GIF export, MP4 video import, and invalid JSON handling.

Run locally:
```bash
pip install pytest playwright
python -m playwright install chromium
python -m pytest -q tests_smoke.py
```
The tests inject the app assets into an isolated browser document and supply a test-scoped localStorage shim because the document uses an opaque origin; they do not contact the network. A small two-second MP4 fixture is in `tests/testsrc_2s.mp4`.

## Android emulator smoke
The `Build GlyphForge Android APK` workflow also installs the debug APK on a stock Android emulator, launches MainActivity, checks the process, saves a screenshot, and scans Android's crash log. It tests launching on generic Android, **not** physical Glyph Matrix LEDs or Nothing OS.

## Remaining hardware requirements
The real Nothing Phone (4a) Pro is needed to validate Glyph Matrix SDK register/render/priority, AOD Glyph Toy registration, notification callbacks under Nothing OS battery restrictions, and audio capture from third-party apps. Passing tests must never be represented as proof of those hardware features.

No recordings, notification content, or user files are uploaded by these tests.
