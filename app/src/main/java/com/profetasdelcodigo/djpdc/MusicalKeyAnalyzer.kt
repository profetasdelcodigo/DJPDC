package com.profetasdelcodigo.djpdc

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin

enum class MusicalMode { MAJOR, MINOR }

data class MusicalKeyProfile(
    val tonic: Int,
    val mode: MusicalMode,
    val confidence: Float
) {
    val displayName: String
        get() = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            .getOrElse(tonic) { "?" } + if (mode == MusicalMode.MINOR) "m" else ""
}

object MusicalKeyAnalyzer {
    private const val FFT_SIZE = 4096
    private const val HOP_SIZE = 2048
    private val majorProfile = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val minorProfile = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    fun analyze(samples: FloatArray, sampleRate: Int): MusicalKeyProfile? {
        if (sampleRate !in 8_000..192_000 || samples.size < sampleRate * 8) return null
        if (samples.any { !it.isFinite() }) return null

        val chroma = DoubleArray(12)
        var frames = 0
        var offset = 0
        while (offset + FFT_SIZE <= samples.size) {
            val real = DoubleArray(FFT_SIZE)
            val imag = DoubleArray(FFT_SIZE)
            for (i in 0 until FFT_SIZE) {
                val window = 0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))
                real[i] = samples[offset + i].toDouble() * window
            }
            fft(real, imag)
            for (midi in 36..95) {
                val frequency = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
                val bin = (frequency * FFT_SIZE / sampleRate).toInt()
                if (bin in 2 until FFT_SIZE / 2) {
                    val magnitude = hypot(real[bin], imag[bin])
                    chroma[((midi % 12) + 12) % 12] += magnitude
                }
            }
            frames++
            offset += HOP_SIZE
        }
        if (frames < 8 || chroma.sum() <= 1e-9) return null

        val scores = ArrayList<Pair<Double, Pair<Int, MusicalMode>>>()
        for (tonic in 0..11) {
            for (mode in MusicalMode.values()) {
                val template = if (mode == MusicalMode.MAJOR) majorProfile else minorProfile
                val rotated = DoubleArray(12) { i -> template[(i - tonic + 12) % 12] }
                scores += pearson(chroma, rotated) to (tonic to mode)
            }
        }
        val sorted = scores.sortedByDescending { it.first }
        val best = sorted.firstOrNull() ?: return null
        val second = sorted.getOrNull(1)?.first ?: 0.0
        val confidence = ((best.first - second).coerceAtLeast(0.0) / (kotlin.math.abs(best.first) + 1e-9))
            .toFloat().coerceIn(0f, 1f)
        if (!confidence.isFinite() || confidence < 0.015f) return null
        return MusicalKeyProfile(best.second.first, best.second.second, confidence)
    }

    private fun pearson(a: DoubleArray, b: DoubleArray): Double {
        val meanA = a.average()
        val meanB = b.average()
        var numerator = 0.0
        var sumA = 0.0
        var sumB = 0.0
        for (i in a.indices) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            numerator += da * db
            sumA += da * da
            sumB += db * db
        }
        return numerator / (kotlin.math.sqrt(sumA * sumB) + 1e-12)
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var start = 0
            while (start < n) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until length / 2) {
                    val even = start + k
                    val odd = even + length / 2
                    val tr = wr * real[odd] - wi * imag[odd]
                    val ti = wr * imag[odd] + wi * real[odd]
                    real[odd] = real[even] - tr
                    imag[odd] = imag[even] - ti
                    real[even] += tr
                    imag[even] += ti
                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextWr
                }
                start += length
            }
            length = length shl 1
        }
    }
}

object HarmonicTrackSelector {
    fun compatibility(a: MusicalKeyProfile, b: MusicalKeyProfile): Float {
        val semitones = ((a.tonic - b.tonic + 12) % 12)
        val distance = minOf(semitones, 12 - semitones)
        return when {
            distance == 0 && a.mode == b.mode -> 1.0f
            distance == 0 -> 0.88f
            distance == 5 || distance == 7 -> 0.78f
            distance == 2 || distance == 10 -> 0.58f
            else -> 0.25f
        }
    }

    fun select(
        ranked: List<RankedLocalTrack>,
        current: MusicalKeyProfile?,
        measured: Map<Long, MusicalKeyProfile>,
        currentEnergy: TrackEnergyProfile? = null,
        measuredEnergy: Map<Long, TrackEnergyProfile> = emptyMap()
    ): RankedLocalTrack? {
        if (ranked.isEmpty()) return null
        if (current == null && currentEnergy == null) return ranked.first()
        return ranked.maxWithOrNull(
            compareBy<RankedLocalTrack> { candidate ->
                var score = candidate.score * 0.15
                measured[candidate.track.id]?.let { key ->
                    if (current != null) score += compatibility(current, key) * 3.0
                }
                val energy = measuredEnergy[candidate.track.id]
                if (currentEnergy != null && energy != null) {
                    score -= kotlin.math.abs(energy.energy - currentEnergy.energy) * 60.0
                    score -= kotlin.math.abs(energy.transientDensity - currentEnergy.transientDensity) * 12.0
                }
                score
            }.thenBy { -it.track.id }
        )
    }
}
