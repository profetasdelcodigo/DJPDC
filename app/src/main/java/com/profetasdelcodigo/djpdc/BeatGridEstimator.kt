package com.profetasdelcodigo.djpdc

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estimates a repeating pulse phase from detected transients.
 *
 * The phase is a timing reference only. It is not a musical downbeat/bar-one
 * detector, and callers must not treat it as a guaranteed cue point.
 */
data class BeatGridEstimate(
    val bpm: Float,
    val beatPeriodMs: Double,
    val phaseOffsetMs: Double,
    val confidence: Float,
    val supportingOnsets: Int
)

object BeatGridEstimator {
    fun estimate(
        onsetTimesMs: List<Long>,
        bpm: Float?,
        minimumConfidence: Float = 0.35f
    ): BeatGridEstimate? {
        if (bpm == null || !bpm.isFinite() || bpm !in 40f..240f) return null
        if (!minimumConfidence.isFinite() || minimumConfidence !in 0f..1f) return null
        val times = onsetTimesMs.filter { it >= 0L }.distinct().sorted()
        if (times.size < 5) return null

        val period = 60_000.0 / bpm.toDouble()
        val angles = times.map { time ->
            val phase = (time.toDouble() % period) / period
            phase * 2.0 * PI
        }
        val x = angles.sumOf(::cos) / angles.size
        val y = angles.sumOf(::sin) / angles.size
        if (!x.isFinite() || !y.isFinite() || kotlin.math.hypot(x, y) < 1e-6) return null

        var phase = (kotlin.math.atan2(y, x) / (2.0 * PI) + 1.0) % 1.0
        val tolerance = period * 0.12
        val phaseMs = phase * period
        val supporting = times.count { time ->
            val raw = kotlin.math.abs((time.toDouble() % period) - phaseMs)
            minOf(raw, period - raw) <= tolerance
        }
        val confidence = supporting.toFloat() / times.size
        if (confidence < minimumConfidence) return null

        return BeatGridEstimate(
            bpm = bpm,
            beatPeriodMs = period,
            phaseOffsetMs = phaseMs,
            confidence = confidence,
            supportingOnsets = supporting
        )
    }
}
