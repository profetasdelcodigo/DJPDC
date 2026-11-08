package com.profetasdelcodigo.djpdc

/**
 * Chooses the closest tempo-compatible candidate from already-ranked local
 * tracks. It does not claim beat alignment: no beat grid or downbeat is used.
 */
data class BpmTrackSelection(
    val track: RankedLocalTrack,
    val playbackSpeedRatio: Float
)

object BpmAwareTrackSelector {
    fun select(
        ranked: List<RankedLocalTrack>,
        currentBpm: Float?,
        measuredBpms: Map<Long, Float>
    ): BpmTrackSelection? {
        if (ranked.isEmpty() || currentBpm == null ||
            !currentBpm.isFinite() || currentBpm !in 40f..240f
        ) return null

        return ranked.mapNotNull { candidate ->
            val candidateBpm = measuredBpms[candidate.track.id] ?: return@mapNotNull null
            val ratio = BeatmatchMath.matchingSpeed(currentBpm, candidateBpm)
                ?: return@mapNotNull null
            BpmTrackSelection(candidate, ratio)
        }.minWithOrNull(
            compareBy<BpmTrackSelection> { kotlin.math.abs(kotlin.math.ln(it.playbackSpeedRatio.toDouble())) }
                .thenByDescending { it.track.score }
                .thenBy { it.track.track.id }
        )
    }
}
