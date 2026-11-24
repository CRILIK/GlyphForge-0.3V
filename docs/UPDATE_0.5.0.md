# GlyphForge 0.5.0

Security: the update permission and installer were removed; external GitHub releases remain the only update path. A static security guardrail, APK SHA-256, certificate and permission inventory are generated in CI; this is not an antivirus verdict. SDK source is pinned. Persistent signing requires owner-controlled secrets. No Play Protect bypass.

Diagnostics: full backup synthetic round trip, bundled MP4 decoded by the existing video pipeline, own quiet 440 Hz AudioTrack/Visualizer probe, certificate SHA-256, explicit blocked status for missing GitHub release, aggregate notification/call event journal without content or identities, direct download completion monitoring. No physical LED confirmation, third-party audio capture, actual rule execution or authenticated external download is inferred from synthetic results. Journal timestamps are local and included only in a user-exported report.

Backup: user-triggered complete .gforge backup in Downloads, validated restore replaces library only after confirmation, restored rules disabled. Existing older library exports remain supported as import; rules in older exports are not automatically reactivated. Backup is not encrypted; keep private.

Browser regression tests use a real HTTP origin and production CSP. Fixed 0.4.5 selectors that returned a single node instead of a list. Tests for update, backup, and security are included. Test results are available in Actions, not asserted by this document.
