package com.profetasdelcodigo.djpdc

/**
 * Estimates manually tapped tempo from monotonic timestamps (milliseconds).
 * Uses the median of recent beat intervals to reduce accidental double taps.
 */
object TapTempo {
    fun bpmFromTimestamps(timestampsMs: List<Long>): Float? {
        if (timestampsMs.size < 3) return null
        val intervals = timestampsMs.zipWithNext { a, b -> b - a }
            .filter { it in 250L..1500L }
            .takeLast(6)
        if (intervals.size < 2) return null

        val sorted = intervals.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2].toDouble()
        }
        if (median <= 0.0) return null
        return (60_000.0 / median).toFloat().takeIf { it in 40f..240f }
    }
}
