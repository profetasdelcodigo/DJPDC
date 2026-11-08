package com.profetasdelcodigo.djpdc

/**
 * Computes a conservative beat-aligned cue for two analyzed local tracks.
 *
 * This is a planning primitive: the playback loop must still wait for
 * delayUntilNextBeatMs before starting the incoming deck. It deliberately
 * refuses uncertain grids and tempo changes outside the supported range.
 */
data class BeatAlignedCue(
    val delayUntilNextBeatMs: Long,
    val incomingTrackStartMs: Long,
    val playbackSpeedRatio: Float,
    val confidence: Float
)

object BeatAlignedTransitionPlanner {
    fun plan(
        currentPositionMs: Long,
        currentGrid: BeatGridEstimate?,
        incomingGrid: BeatGridEstimate?,
        minimumConfidence: Float = DJPlanValidator.MIN_BEAT_CONFIDENCE
    ): BeatAlignedCue? {
        if (currentPositionMs < 0L) return null
        if (!minimumConfidence.isFinite() || minimumConfidence !in 0f..1f) return null
        val outgoing = currentGrid ?: return null
        val incoming = incomingGrid ?: return null
        if (!isUsable(outgoing, minimumConfidence) || !isUsable(incoming, minimumConfidence)) return null

        val speedRatio = outgoing.bpm / incoming.bpm
        if (!speedRatio.isFinite() ||
            speedRatio !in DJPlanValidator.MIN_SPEED_RATIO..DJPlanValidator.MAX_SPEED_RATIO
        ) return null

        val period = outgoing.beatPeriodMs
        val phase = outgoing.phaseOffsetMs
        if (!period.isFinite() || period <= 0.0 ||
            !phase.isFinite() || phase !in 0.0 until period
        ) return null

        val positionPhase = currentPositionMs.toDouble() % period
        val rawDelay = (phase - positionPhase + period) % period
        val confidence = minOf(outgoing.confidence, incoming.confidence)
        return BeatAlignedCue(
            delayUntilNextBeatMs = rawDelay.toLong(),
            incomingTrackStartMs = incoming.phaseOffsetMs.toLong(),
            playbackSpeedRatio = speedRatio,
            confidence = confidence
        )
    }

    private fun isUsable(grid: BeatGridEstimate, minimumConfidence: Float): Boolean =
        grid.bpm.isFinite() && grid.bpm in 40f..240f &&
            grid.beatPeriodMs.isFinite() && grid.beatPeriodMs > 0.0 &&
            grid.phaseOffsetMs.isFinite() &&
            grid.phaseOffsetMs >= 0.0 && grid.phaseOffsetMs < grid.beatPeriodMs &&
            grid.confidence.isFinite() && grid.confidence >= minimumConfidence &&
            grid.confidence <= 1f
}
