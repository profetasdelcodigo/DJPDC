package com.profetasdelcodigo.djpdc

/**
 * Deterministic equal-power transition timeline.
 *
 * The Android playback executor can sample this timeline while updating both
 * Media3 player volumes. This class has no timers or player side effects.
 */
data class AutoDJCrossfadeFrame(
    val elapsedMs: Long,
    val progress: Float,
    val gains: CrossfadeGains,
    val isComplete: Boolean
)

object AutoDJCrossfadeTimeline {
    fun frameAt(elapsedMs: Long, durationMs: Long): AutoDJCrossfadeFrame? {
        if (durationMs <= 0L) return null
        val safeElapsed = elapsedMs.coerceIn(0L, durationMs)
        val progress = safeElapsed.toFloat() / durationMs.toFloat()
        return AutoDJCrossfadeFrame(
            elapsedMs = safeElapsed,
            progress = progress,
            gains = MixerMath.equalPowerCrossfade(progress),
            isComplete = safeElapsed >= durationMs
        )
    }

    /**
     * A transition is allowed to continue only while the session is active,
     * both decks still refer to the expected tracks, and the user has not
     * issued a manual override.
     */
    fun mayContinue(
        session: AutoDJSessionState,
        expectedCurrentTrackId: Long,
        expectedNextTrackId: Long,
        actualCurrentTrackId: Long?,
        actualNextTrackId: Long?,
        userOverride: Boolean
    ): Boolean =
        !userOverride &&
            session.mode == AutoDJMode.ACTIVE &&
            session.currentTrackId == expectedCurrentTrackId &&
            actualCurrentTrackId == expectedCurrentTrackId &&
            actualNextTrackId == expectedNextTrackId &&
            expectedCurrentTrackId != expectedNextTrackId
}
