# DJPDC

**AI-powered Android music player and hybrid DJ.**

DJPDC is being built as an Android application with a local-first audio engine and an optional AI layer for music-library analysis and intelligent mixing.

## Product direction

- Android native app built with Kotlin and Jetpack Compose.
- Local music library access through Android MediaStore and user-granted permissions.
- Hybrid experience: normal music player, assisted Auto DJ, and manual two-deck controls.
- Audio playback and timing-sensitive DSP run on-device; cloud AI is not part of the real-time audio path.
- NVIDIA AI services will be accessed only through a secure backend. Never put API keys in the Android app.

## Roadmap

1. Android foundation and local music library.
2. Playback, queue, playlists, and library metadata.
3. Two-deck engine, BPM analysis, beat matching, and transitions.
4. Intelligent track selection and transition suggestions.
5. Performance, accessibility, and device testing.

## Current status

Project initialization. The first milestone is a reliable Android app shell and local-library foundation.
