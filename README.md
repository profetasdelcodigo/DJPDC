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
- Custom playlists saved locally by name, with playlist filtering and playback limited to the selected playlist.
- Album artwork loaded from MediaStore and shown in the now-playing panel using Coil Compose.
- GitHub Actions workflow to attempt a debug APK build on pushes and pull requests.

## Roadmap

1. Validate the Android build on CI and a physical device.
2. Verify background playback service, media notification, lock-screen controls, and queue management.
3. Add playlist editing (rename, remove tracks, delete playlists) and richer metadata; basic local playlists and album artwork are integrated.
4. Add BPM analysis, beat matching, and smoother transitions to the two-deck playback foundation.
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


### Custom playlists (Beta 0.6)
- Save the currently filtered library tracks into a named playlist.
- Playlist names and track IDs persist locally using SharedPreferences.
- Select a playlist to filter the library and play only its tracks.
- Tap the selected playlist again or choose All tracks to return to the full library.


### Mixer foundation (Beta 0.7 groundwork)
- Adds equal-power crossfade gain calculations for Deck A and Deck B.
- The crossfader position is normalized and clamped; center position gives each deck approximately 0.707 gain.
- Adds channel-volume gain calculation and unit tests for endpoints, center balance, clamping, and channel gain.
- This is DSP groundwork only: two independent audio players and an audible crossfade are not yet connected.


### Two-deck playback foundation (Beta 0.8)
- Deck A continues using the primary MediaSession playback service.
- Adds an independent Deck B MediaSession service and ExoPlayer instance.
- Tracks can be loaded into Deck B from the local library and played or paused independently of Deck A.
- Adds an equal-power crossfader that adjusts the actual volume of both players.
- Crossfader endpoints route audio to one deck; the center balances both decks.
- This is the first functional two-player mixing foundation. BPM detection, beat synchronization, EQ, effects, and device-level audio latency tuning remain future work.
