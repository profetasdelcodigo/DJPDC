package com.profetasdelcodigo.djpdc

import kotlin.math.floor

data class BeatPulse(val beatInBar: Int, val phase: Float)

/**
 * Position within a four-beat bar, estimated from playback position and BPM.
 * This is a tempo ruler only; it does not detect the track's true downbeat.
 */
object BeatPulseMath {
    fun at(positionMs: Long, bpm: Float?): BeatPulse? {
        if (positionMs < 0L || bpm == null || !bpm.isFinite() || bpm !in 40f..240f) return null
        val beatDurationMs = 60_000.0 / bpm.toDouble()
        val beatPosition = positionMs / beatDurationMs
        if (!beatPosition.isFinite()) return null
        val wholeBeat = floor(beatPosition).toLong()
        val phase = (beatPosition - wholeBeat).toFloat().coerceIn(0f, 0.999999f)
        return BeatPulse(beatInBar = (wholeBeat % 4L).toInt() + 1, phase = phase)
    }
}
