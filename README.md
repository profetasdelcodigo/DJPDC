# DJPDC

**AI-powered Android music player and hybrid DJ.**

DJPDC is an Android application built around a local-first audio engine, with an optional AI layer for music-library analysis and intelligent mixing.

## Product direction

- Native Android app using Kotlin and Jetpack Compose.
- Local music discovery through Android MediaStore and explicit user permission.
- Hybrid experience: everyday music player, assisted Auto DJ, and future manual two-deck controls.
- Playback and timing-sensitive DSP run on-device; cloud AI is not part of the real-time audio path.
- NVIDIA AI services will be accessed only through a secure backend. Never place API keys in the Android app.

## Implemented

- Android project foundation and dark DJPDC interface.
- Runtime permission request for local audio access.
- Local audio-library scan using MediaStore.
- Track list with title, artist, and album metadata.
- Local playback and next-track control using AndroidX Media3.
- Dedicated MediaSessionService so playback can continue when the activity is no longer visible.
- MediaController connection between the app interface and the background playback session.
- Now-playing state updates and manual library rescan.
- GitHub Actions workflow to attempt a debug APK build on pushes and pull requests.

## Roadmap

1. Validate the Android build on CI and a physical device.
2. Verify background playback service, media notification, lock-screen controls, and queue management.
3. Add album artwork, playlists, favorites, search, and richer metadata.
4. Build the two-deck engine, BPM analysis, beat matching, and transitions.
5. Add intelligent track selection and transition suggestions through a secure backend.
6. Optimize audio performance and test across Android devices.

## Build

The repository includes a GitHub Actions workflow at `.github/workflows/android-ci.yml` that runs:

```bash
gradle --no-daemon assembleDebug
```

A successful workflow run is still required before treating the current source as a verified build or installable APK.
