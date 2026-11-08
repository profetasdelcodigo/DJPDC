# DJPDC

**AI-first autonomous Android DJ.**

DJPDC is designed so the AI handles the DJ work end to end. The default experience will be natural-language requests and simple playback controls—not a professional DJ panel. Decks, BPM, cue points, EQ, crossfader and transitions are internal tools managed by the local engine. See [the autonomous DJ product specification](docs/AUTONOMOUS_DJ_SPEC.md).

## Product direction

- Native Android app using Kotlin and Jetpack Compose.
- Local music discovery through Android MediaStore and explicit user permission.
- AI-first experience: the user describes what they want; Auto DJ selects tracks and manages transitions. Advanced manual controls are optional and hidden by default.
- Playback, mixing and timing-sensitive DSP run on-device; cloud AI plans and reasons but is never in the real-time audio path.
- Auto DJ requires an explicit user enable action and always yields to Pause, Stop, Auto DJ Off and manual override.
- The AI-first product direction is specified in `docs/AUTONOMOUS_DJ_SPEC.md`; this is a target architecture, not a claim that autonomous AI mixing is already implemented.
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
5. Define a validated local DJ controller API and deterministic transition executor with safe fallbacks.
6. Build beat-grid/downbeat confidence and track-structure analysis from local audio.
7. Add the AI planner through a secure backend, with local fallback and natural-language controls.
8. Hide professional DJ controls in the default UI; validate end-to-end autonomous operation on physical Android devices.

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
- Adds independent Deck A and Deck B channel-volume faders, combined with crossfader gain.
- Adds a Deck B return-to-start control that pauses the deck and seeks to the beginning of its loaded track.
- Adds a manual tap-tempo control for Deck B: three or more taps estimate BPM from recent intervals using a median filter; this is manual tempo measurement, not automatic audio-track BPM detection.
- Adds independent temporary CUE points for Deck A and Deck B: set a marker at the current playback position and jump back to it during a mix. Cue markers reset when a different track is loaded into that deck and are currently session-only.
- Adds manual tap-tempo measurement for both Deck A and Deck B.
- Adds a bounded beatmatch control to adjust Deck B playback speed toward Deck A's measured BPM, with a maximum correction of ±8% and pitch-preserving playback parameters; the adjustment can be reset to normal speed.
- Beatmatch speed calculations have unit tests for matching, adjustment limits, and invalid tempo values. This is manual BPM-based tempo matching; automatic beat-grid alignment and phase synchronization are not yet implemented.
- Adds unit tests for tap-tempo calculation, minimum taps, outlier resistance, and supported tempo range.
- Crossfader endpoints route audio to one deck; the center balances both decks.
- This is the first functional two-player mixing foundation with per-deck level controls and manual tap-tempo measurement. Automatic audio BPM detection, beat synchronization, EQ, effects, and device-level audio latency tuning remain future work.


### Automatic BPM analysis groundwork
- Adds a pure Kotlin PCM tempo estimator based on short-window RMS energy, positive energy changes, and onset-envelope autocorrelation.
- Includes synthetic 100 BPM and 120 BPM audio tests, plus silence, short-input, invalid-rate, and non-finite sample checks.
- The estimator accepts decoded mono PCM samples; MediaExtractor/MediaCodec decoding and automatic per-track UI integration are not yet connected. Do not treat this as automatic BPM detection in the app until that integration is complete and device-tested.


### Automatic BPM decoding integration
- Adds an Android MediaExtractor/MediaCodec decoder that reads up to 45 seconds of a selected local audio track and converts decoded PCM to mono samples.
- Adds AUTO BPM A and AUTO BPM B controls; decoding and tempo estimation run on Dispatchers.IO so the interface remains responsive.
- Successful estimates populate the corresponding BPM value used by the manual beatmatch control. Unsupported formats, short clips, silence, and decode failures report that no estimate could be made.
- Accuracy varies with rhythm, intros, syncopation, and codec output. Physical-device testing with varied music files is still required; this is an estimate, not a guaranteed beat grid.


### Approximate beat pulse
- Deck A and Deck B display a four-beat pulse ruler derived from each deck's current playback position and measured/estimated BPM.
- The indicator reports beat-in-bar and fractional progress through the beat.
- This is a tempo-based reference only: it does not identify the true musical downbeat or guarantee phase alignment. Use listening and manual cue adjustment for precise mixing.


### Manual Deck B phase nudge
- Adds small ±20 ms seek adjustments for Deck B so a user can manually refine the relative timing while listening.
- Nudge calculations clamp to the track boundaries and are covered by unit tests.
- This is a seek-based manual aid, not automatic transient detection, downbeat detection, phase lock, or guaranteed synchronization. Seek precision depends on the media format and Android playback pipeline.

### Local audio onset detection groundwork
- Adds a pure Kotlin detector that analyzes decoded mono PCM in short overlapping RMS windows and identifies likely transient onsets from positive energy changes.
- Returns estimated onset timestamps and a normalized relative-strength indicator; invalid input is rejected and silence returns an empty result.
- Adds synthetic transient, silence, and invalid-input unit tests.
- This is offline onset detection groundwork only. It does not yet determine musical beats, downbeats, tempo, or phase, and is not connected to the playback path or Auto DJ. Accuracy on real mixed music and device performance still require testing.

### Autonomous DJ plan guardrails (controller foundation)
- Adds a typed transition-plan contract for AI-proposed next-track selection, transition duration, start position, playback-speed ratio, beat-alignment confidence and transition style.
- Adds a deterministic local validator that requires Auto DJ to be enabled, verifies both active and next-track identity against the accessible local library, and bounds all timing and speed values.
- When beat-alignment confidence is below the configured threshold, a proposed beat-matched transition is downgraded to a simple fade at normal speed and the start of the next track.
- Adds unit tests for valid plans, disabled Auto DJ, inaccessible/same tracks, invalid duration and position, unsafe speed, invalid confidence, unknown duration and low-confidence fallback.
- This is a validated planning contract only. It is not yet connected to Media3 playback, does not start music, and does not mean the AI planner or autonomous mixing is implemented.

### Local music library only — product requirement
- Auto DJ must select and play only audio tracks available in the user's permitted Android music library (MediaStore).
- No silent fallback to streaming services, YouTube, Spotify, radio or remote audio catalogs. If there is no suitable local match, explain that and offer the closest available tracks on the phone.
- Re-scan the library only with permission and handle removed or unreadable files gracefully.
- Audio analysis stays on-device by default. Cloud planning receives only the minimum metadata required; audio uploads or external library sharing require separate explicit consent.
- Users may optionally limit selection to favorites or a chosen playlist.
