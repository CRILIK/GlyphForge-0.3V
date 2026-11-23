# Music Reactive · implementation and system limitations

## Preview and data model

The new `music.js` module adds a local-only collection of profile documents (`state.musicProfiles`). A `.gforgebeat` file has `{ "kind": "glyphforge-music-profile", "version": 1, "profile": { ... } }` and carries **settings and IDs**, not copyrighted audio. If a profile references a design ID from another person's library, users must import that design separately; otherwise the editor falls back to an available design.

The Web Audio preview attaches an AnalyserNode to the local audio element and converts frequency bins and RMS into real-time monochrome LED frames. The audio source never leaves the browser.

## Android implementation

`MainActivity` requests `RECORD_AUDIO` and starts a foreground `MusicVisualizerService` following user interaction. The service offers:

1. `Visualizer(0)` (global output mix), requiring `MODIFY_AUDIO_SETTINGS` and record-audio permission. Restricted on some devices.
2. `MediaProjection` approved by Android each time, paired with `AudioPlaybackCaptureConfiguration` (only usages MEDIA/GAME, only apps permitting capture). Android 14+ requires the `mediaProjection` foreground-service type, the associated permission and proper token lifecycle.

The service derives 8 frequency bands, RMS and beat onset, smooths them and builds a 13×13 frame using `MusicFrameRenderer`. `GlyphPlayer.playLiveFrame` streams frames using Nothing's structured `GlyphMatrixFrame` SDK path. No bytes are written to storage. The service stops after a user action or when the system kills it; it is intentionally **not** restarted on boot, and does not start foreground capture from background app events.

## State of testing

- JavaScript functionality tested headlessly in Chromium: catalog/search, editing and saving a profile, demo rendering, Web Audio analysis of a local synthesized WAV.
- Android Java syntax parsing: see `docs/TEST_REPORT.md` (syntax parsing is not an APK build).
- Not tested: compiling APK against Android SDK 36, MediaProjection on actual hardware, DRM exclusion behavior, Nothing SDK LED output, foreground-service persistence, battery use.

## Known issues and future work

- MediaProjection session ends when Android revokes consent; it must be requested again.
- DRM and explicit audio capture exclusions can't be bypassed.
- Nothing Glyph Matrix priority policy may temporarily interrupt third-party LED output.
- A client/server community library is *not* implemented. The application links to external catalogs and allows user-initiated import of public downloadable files.
- Full audio-ringtone generation with system call synchronization is **not** part of Music Reactive.
