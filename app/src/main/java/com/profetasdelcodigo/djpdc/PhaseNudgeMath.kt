package com.profetasdelcodigo.djpdc

/**
 * Applies a small manual timing correction to Deck B's playback position.
 * This is a seek-based nudge, not automatic beat detection or phase lock.
 */
object PhaseNudgeMath {
    fun adjust(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        if (positionMs < 0L || durationMs < 0L) return 0L
        val upperBound = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        val adjusted = positionMs.toDouble() + deltaMs.toDouble()
        return adjusted.coerceIn(0.0, upperBound.toDouble()).toLong()
    }
}
