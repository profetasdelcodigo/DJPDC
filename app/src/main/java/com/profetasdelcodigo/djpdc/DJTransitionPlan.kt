package com.profetasdelcodigo.djpdc

/**
 * A bounded transition proposal from the AI planner.
 *
 * Track IDs must come from the user's already-permitted local library.
 * This object is a proposal only; a local executor must validate it before playback changes.
 */
enum class DJTransitionStyle {
    BEAT_MATCHED_CROSSFADE,
    SIMPLE_FADE
}

data class DJTransitionPlan(
    val nextTrackId: Long,
    val transitionDurationMs: Long,
    val nextTrackStartMs: Long,
    val playbackSpeedRatio: Float,
    val beatAlignmentConfidence: Float,
    val style: DJTransitionStyle
)

data class DJPlanContext(
    val autoDjEnabled: Boolean,
    val currentTrackId: Long?,
    val accessibleTrackIds: Set<Long>,
    val nextTrackDurationMs: Long
)

sealed class DJPlanDecision {
    data class Accepted(val plan: DJTransitionPlan, val usedSafeFallback: Boolean) : DJPlanDecision()
    data class Rejected(val reason: String) : DJPlanDecision()
}

/**
 * Deterministic local guardrail for plans proposed by an AI service.
 * No player or network APIs are called here.
 */
object DJPlanValidator {
    const val MIN_TRANSITION_MS = 2_000L
    const val MAX_TRANSITION_MS = 20_000L
    const val MIN_SPEED_RATIO = 0.92f
    const val MAX_SPEED_RATIO = 1.08f
    const val MIN_BEAT_CONFIDENCE = 0.70f

    fun validate(plan: DJTransitionPlan, context: DJPlanContext): DJPlanDecision {
        if (!context.autoDjEnabled) {
            return DJPlanDecision.Rejected("Auto DJ is disabled")
        }
        if (context.currentTrackId == null) {
            return DJPlanDecision.Rejected("There is no active track")
        }
        if (plan.nextTrackId == context.currentTrackId) {
            return DJPlanDecision.Rejected("The next track must be different")
        }
        if (plan.nextTrackId !in context.accessibleTrackIds) {
            return DJPlanDecision.Rejected("The next track is not in the accessible library")
        }
        if (context.nextTrackDurationMs <= 0L) {
            return DJPlanDecision.Rejected("The next track duration is unknown")
        }
        if (plan.transitionDurationMs !in MIN_TRANSITION_MS..MAX_TRANSITION_MS) {
            return DJPlanDecision.Rejected("Transition duration is outside the allowed range")
        }
        if (plan.nextTrackStartMs !in 0 until context.nextTrackDurationMs) {
            return DJPlanDecision.Rejected("The proposed start position is outside the track")
        }
        if (!plan.playbackSpeedRatio.isFinite() ||
            plan.playbackSpeedRatio !in MIN_SPEED_RATIO..MAX_SPEED_RATIO
        ) {
            return DJPlanDecision.Rejected("Playback speed is outside the safe range")
        }
        if (!plan.beatAlignmentConfidence.isFinite() ||
            plan.beatAlignmentConfidence !in 0f..1f
        ) {
            return DJPlanDecision.Rejected("Beat alignment confidence is invalid")
        }

        val needsFallback = plan.style == DJTransitionStyle.BEAT_MATCHED_CROSSFADE &&
            plan.beatAlignmentConfidence < MIN_BEAT_CONFIDENCE

        val safePlan = if (needsFallback) {
            plan.copy(
                nextTrackStartMs = 0L,
                playbackSpeedRatio = 1f,
                style = DJTransitionStyle.SIMPLE_FADE
            )
        } else {
            plan
        }
        return DJPlanDecision.Accepted(safePlan, needsFallback)
    }
}
