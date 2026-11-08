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
- Album artwork loaded from Android MediaStore and displayed in the now-playing panel, with a branded fallback when artwork is unavailable.
- Local playback and next-track control using AndroidX Media3.
- Dedicated MediaSessionService so playback can continue when the activity is no longer visible.
- MediaController connection between the app interface and the background playback session.
- Now-playing state updates and manual library rescan.
- Instant library search across song titles, artists, and albums.
- Shuffle playback and repeat modes (off, all, and one).
- Favorites saved locally and restored between app sessions, with an all-tracks/favorites filter.
- GitHub Actions workflow to attempt a debug APK build on pushes and pull requests.

## Roadmap

1. Validate the Android build on CI and a physical device.
2. Verify background playback service, media notification, lock-screen controls, and queue management.
3. Add user-created playlists and richer metadata; album artwork is now integrated.
4. Build the two-deck engine, BPM analysis, beat matching, and transitions.
5. Add intelligent track selection and transition suggestions through a secure backend.
6. Optimize audio performance and test across Android devices.

## Build

The repository includes a GitHub Actions workflow at `.github/workflows/android-ci.yml` that runs:

```bash
gradle --no-daemon assembleDebug
```

A successful workflow run is still required before treating the current source as a verified build or installable APK.

### Playback improvements (Beta 0.4)
- Live playback progress with elapsed and total time.
- Seek control to jump within the current track.
- Upcoming queue preview showing the next tracks in the active library queue.


### Album artwork (Beta 0.5)
- Reads each track album ID from MediaStore.
- Builds the local album-art content URI and loads artwork with Coil for Compose.
- Shows a DJPDC fallback tile when a track has no embedded or indexed artwork.
