# DJPDC — Autonomous DJ Product Specification

## Non-negotiable product principle

The default DJPDC experience is operated by the AI. The user must not need DJ knowledge or operate professional DJ controls. Decks, BPM, beat grids, cue points, EQ, effects, gain staging, crossfader, transition timing and queue management are internal implementation details.

The primary experience is a simple conversation/request interface and a clear playback screen. Advanced manual controls may exist only in an optional Advanced Mode and must never be required for ordinary use.

## User experience

The user can make natural-language requests such as:
- "Pon música para estudiar."
- "Quiero música alegre para una reunión."
- "Mezcla canciones parecidas a esta."
- "Baja un poco la energía."
- "Sorpréndeme con música de Piura."

The AI should interpret intent, ask a short clarification only when genuinely necessary, and then manage the session. The user can always say "pausa", "siguiente", "más tranquilo", "cambia el estilo" or "detén el DJ".

The main screen should show:
- A prominent AI DJ status (Listening, Preparing, Mixing, Playing, Paused).
- Current song and artist, artwork and simple playback controls.
- A natural-language request input and optional voice input.
- A compact explanation of what the DJ is doing, without technical jargon.
- A clear Auto DJ on/off control and an immediate Stop/Pause override.

Do not expose two-deck panels, BPM numbers, waveform editing, cue controls, EQ sliders or crossfader in the default mode.

## Music source — user's phone library only

- The only source for tracks that DJPDC selects and plays is the user's own accessible music library on the Android device (MediaStore content URIs obtained with the user's permission).
- The AI must select only tracks present in the current library inventory. It must never fabricate track entries or silently substitute streaming, YouTube, Spotify, radio, cloud catalogs or remote audio.
- If a requested genre, artist, mood or language is not represented in the local library, explain that the library does not currently contain a suitable match and offer the closest available local alternatives. Never pretend a missing track exists.
- Re-scan or refresh the library only with appropriate Android permission. Handle removed, moved, revoked or unreadable files gracefully.
- Keep audio analysis on-device by default. Cloud AI may receive only the minimum metadata needed for planning; do not upload audio files or share the library externally without separate, explicit user consent.
- The user can exclude tracks or folders and can ask the AI to use favorites or a chosen playlist as its selection pool.

## Autonomous responsibilities

When Auto DJ is enabled, the system should:
1. Understand the user's request, mood, genre, language, energy, occasion and constraints.
2. Inspect the user's permitted local music library and available metadata.
3. Select suitable tracks and maintain a coherent session.
4. Analyze candidate audio locally for duration, tempo, likely beats, downbeats, loudness and transient structure, with confidence scores.
5. Choose safe transition points and prepare the next deck in the background.
6. Match tempo and timing within conservative limits; choose a simpler fade when confidence is low.
7. Execute crossfades and levels locally, without requiring cloud round trips for timing-critical actions.
8. Monitor playback state and recover from unsupported files, analysis failures, interruptions and missing metadata.
9. Learn session preferences only with transparent, user-controllable settings.
10. Explain decisions in plain language and accept immediate corrections.

## Architecture boundary

- On-device playback and mixing engine owns all timing-critical operations.
- A local DJ session controller exposes a small, validated set of actions: play, pause, stop, load candidate, seek to approved cue, set bounded playback speed, set channel gain and execute a bounded transition plan.
- AI planner proposes structured plans; it never directly manipulates arbitrary player internals.
- Validate every plan locally: track belongs to the accessible library, values are in range, deck state is valid, transition duration is bounded, and playback is not started unexpectedly.
- NVIDIA/cloud AI is for intent understanding, selection reasoning and non-real-time planning. It is optional and may be unavailable.
- Never place provider API keys in the Android app. Use a secure backend for cloud calls.
- Local fallback behavior must keep playback usable when internet or AI services are unavailable.

## Autonomy and user control

- Auto DJ must be explicitly enabled by the user before it starts making automatic transitions.
- It may continue a session only while Auto DJ remains enabled.
- Pause, Stop and Auto DJ Off must take priority over every AI plan.
- The user can skip, reject a song, change the request or take manual control at any time.
- No purchases, subscriptions, uploads or external sharing without separate explicit consent.
- Do not claim the AI is listening to the microphone unless microphone permission and a clearly visible listening state are active.

## Reliability and honest status

- Show confidence/uncertainty internally and use conservative fades when beat/downbeat detection is weak.
- Never describe an estimated BPM or beat grid as guaranteed.
- Do not claim autonomous mixing is implemented until planner-to-controller integration, local validation, failure handling and device testing pass.
- Test with varied real music, silence, intros, syncopated rhythms, variable tempo, corrupt/unsupported files, Bluetooth audio routes, phone calls and background playback.
- Current repository features are foundations only: two independent players, manual mixer controls, experimental BPM estimation, approximate beat pulse, seek-based phase nudge and offline onset detection groundwork. The autonomous AI planner, voice interface, beat-grid/downbeat engine and automatic transition execution are not yet implemented.

## Delivery sequence

1. Keep advanced controls functional but move the default product direction to AI-first.
2. Define and test a local validated DJ action/controller API.
3. Build robust local audio analysis: transients, beat candidates, downbeat confidence and track structure.
4. Build a deterministic transition executor with bounded plans, fallback fades and immediate user override.
5. Add the AI planner through a secure backend, with a local fallback.
6. Build the conversational/voice-first interface and hide professional controls by default.
7. Test end-to-end on physical Android devices before calling the product autonomous.
