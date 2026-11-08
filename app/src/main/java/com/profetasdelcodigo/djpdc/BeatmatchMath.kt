package com.profetasdelcodigo.djpdc

/**
 * Calculates a playback-speed correction for matching Deck B to Deck A.
 * Returns null when the required correction exceeds the supported range;
 * silently clamping such a correction would falsely claim the decks match.
 *
 * A pitch value of 1.0 is used by the player so supported media playback
 * time-stretching can preserve musical pitch while changing tempo.
 */
object BeatmatchMath {
    const val MAX_ADJUSTMENT = 0.08f

    fun matchingSpeed(deckABpm: Float?, deckBBpm: Float?): Float? {
        if (deckABpm == null || deckBBpm == null) return null
        if (!deckABpm.isFinite() || !deckBBpm.isFinite()) return null
        if (deckABpm !in 40f..240f || deckBBpm !in 40f..240f) return null

        val requiredSpeed = deckABpm / deckBBpm
        return requiredSpeed.takeIf {
            it.isFinite() && it in (1f - MAX_ADJUSTMENT)..(1f + MAX_ADJUSTMENT)
        }
    }
}
