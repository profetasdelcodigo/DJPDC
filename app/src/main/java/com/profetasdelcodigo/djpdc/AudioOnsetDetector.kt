package com.profetasdelcodigo.djpdc

import kotlin.math.sqrt

/**
 * Finds likely transient onsets in decoded mono PCM.
 *
 * This is an offline analysis helper, not a real-time audio callback or a beat-grid detector.
 * Call it from a worker thread for longer recordings.
 */
data class OnsetDetection(
    val onsetTimesMs: List<Long>,
    val confidence: Float
)

object AudioOnsetDetector {
    fun detect(
        samples: FloatArray,
        sampleRate: Int,
        windowSize: Int = 1024,
        hopSize: Int = 256
    ): OnsetDetection? {
        if (sampleRate !in 8_000..192_000) return null
        if (windowSize < 128 || hopSize !in 1..windowSize) return null
        if (samples.size < sampleRate / 2 || samples.any { !it.isFinite() }) return null

        val rms = ArrayList<Double>()
        var offset = 0
        while (offset + windowSize <= samples.size) {
            var sum = 0.0
            for (i in offset until offset + windowSize) {
                val value = samples[i].toDouble()
                sum += value * value
            }
            rms.add(sqrt(sum / windowSize))
            offset += hopSize
        }
        if (rms.size < 3) return null

        val novelty = DoubleArray(rms.size)
        for (i in 1 until rms.size) {
            novelty[i] = (rms[i] - rms[i - 1]).coerceAtLeast(0.0)
        }
        val sorted = novelty.drop(1).sorted()
        val median = sorted[sorted.size / 2]
        val mean = novelty.average()
        val variance = novelty.sumOf { (it - mean) * (it - mean) } / novelty.size
        val deviation = sqrt(variance)
        val peak = novelty.maxOrNull() ?: 0.0
        if (peak <= 1e-8) return OnsetDetection(emptyList(), 0f)

        val threshold = maxOf(median * 2.5, mean + deviation * 0.75, peak * 0.12)
        val minGapFrames = (sampleRate * 0.09 / hopSize).toInt().coerceAtLeast(1)
        val candidates = ArrayList<Pair<Int, Double>>()

        for (i in 1 until novelty.lastIndex) {
            val value = novelty[i]
            if (value >= threshold && value >= novelty[i - 1] && value > novelty[i + 1]) {
                val previous = candidates.lastOrNull()
                if (previous == null || i - previous.first >= minGapFrames) {
                    candidates.add(i to value)
                } else if (value > previous.second) {
                    candidates[candidates.lastIndex] = i to value
                }
            }
        }

        val times = candidates.map { (frame, _) ->
            ((frame.toLong() * hopSize * 1000L) / sampleRate)
        }
        val confidence = if (candidates.isEmpty()) 0f else {
            (candidates.map { it.second }.average() / (peak + 1e-12))
                .toFloat().coerceIn(0f, 1f)
        }
        return OnsetDetection(times, confidence)
    }
}
