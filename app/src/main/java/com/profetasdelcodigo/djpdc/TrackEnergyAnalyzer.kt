package com.profetasdelcodigo.djpdc

import kotlin.math.sqrt

/**
 * Compact, deterministic music-energy analysis for decoded mono PCM.
 *
 * The result is a relative mix-planning feature, not a genre classifier or
 * a claim about how people will perceive a song. Run it off the audio thread.
 */
data class TrackEnergyProfile(
    val energy: Float,
    val peakEnergy: Float,
    val dynamicRange: Float,
    val transientDensity: Float,
    val analyzedSeconds: Float
)

object TrackEnergyAnalyzer {
    fun analyze(samples: FloatArray, sampleRate: Int): TrackEnergyProfile? {
        if (sampleRate !in 8_000..192_000 || samples.size < sampleRate * 4) return null
        if (samples.any { !it.isFinite() }) return null

        val frameSize = (sampleRate / 10).coerceAtLeast(1) // 100 ms envelope
        val rms = ArrayList<Double>()
        var offset = 0
        while (offset + frameSize <= samples.size) {
            var sum = 0.0
            for (i in offset until offset + frameSize) {
                val value = samples[i].toDouble().coerceIn(-1.0, 1.0)
                sum += value * value
            }
            rms += sqrt(sum / frameSize)
            offset += frameSize
        }
        if (rms.size < 20) return null

        val sorted = rms.sorted()
        fun percentile(fraction: Double): Double {
            val index = (fraction * (sorted.size - 1)).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }

        val reference = percentile(0.95).coerceAtLeast(1e-8)
        val mean = rms.average()
        val energy = (mean / reference).toFloat().coerceIn(0f, 1f)
        val peak = (rms.maxOrNull()!! / reference).toFloat().coerceIn(0f, 1f)
        val dynamicRange = ((percentile(0.90) - percentile(0.20)) / reference)
            .toFloat().coerceIn(0f, 1f)

        val threshold = mean * 1.45
        var transients = 0
        for (i in 1 until rms.size) {
            if (rms[i] > threshold && rms[i] > rms[i - 1] * 1.18) transients++
        }
        val seconds = samples.size.toFloat() / sampleRate
        val transientDensity = (transients / seconds / 4f).coerceIn(0f, 1f)

        return TrackEnergyProfile(
            energy = energy,
            peakEnergy = peak,
            dynamicRange = dynamicRange,
            transientDensity = transientDensity,
            analyzedSeconds = seconds
        )
    }
}

/**
 * Ranks already-permitted local tracks by the energy continuity of a transition.
 * Metadata score remains a small tie-breaker; missing analysis never excludes a track.
 */
object EnergyAwareTrackSelector {
    fun select(
        ranked: List<RankedLocalTrack>,
        current: TrackEnergyProfile?,
        measured: Map<Long, TrackEnergyProfile>
    ): RankedLocalTrack? {
        if (ranked.isEmpty()) return null
        if (current == null) return ranked.first()

        return ranked.maxWithOrNull(
            compareBy<RankedLocalTrack> { candidate ->
                val profile = measured[candidate.track.id]
                if (profile == null) candidate.score.toDouble()
                else {
                    val energyGap = kotlin.math.abs(profile.energy - current.energy)
                    val transientGap = kotlin.math.abs(profile.transientDensity - current.transientDensity)
                    candidate.score * 0.15 - energyGap * 60.0 - transientGap * 12.0
                }
            }.thenBy { -it.track.id }
        )
    }
}
