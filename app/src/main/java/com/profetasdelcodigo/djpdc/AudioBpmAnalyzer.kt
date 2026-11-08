package com.profetasdelcodigo.djpdc

import kotlin.math.sqrt

/**
 * Estimates a track tempo from decoded mono PCM samples.
 * This class does not decode media files; callers must provide PCM on a worker thread.
 */
object AudioBpmAnalyzer {
    fun estimateBpm(samples: FloatArray, sampleRate: Int): Float? {
        if (sampleRate !in 8_000..192_000 || samples.size < sampleRate * 8) return null
        if (samples.any { !it.isFinite() }) return null

        val windowSize = 1024
        val hopSize = 512
        if (samples.size < windowSize) return null

        val energy = ArrayList<Double>()
        var offset = 0
        while (offset + windowSize <= samples.size) {
            var sum = 0.0
            for (i in offset until offset + windowSize) {
                val value = samples[i].toDouble()
                sum += value * value
            }
            energy.add(sqrt(sum / windowSize))
            offset += hopSize
        }
        if (energy.size < 8) return null

        val onset = DoubleArray(energy.size)
        for (i in 1 until energy.size) {
            onset[i] = (energy[i] - energy[i - 1]).coerceAtLeast(0.0)
        }
        val mean = onset.average()
        for (i in onset.indices) onset[i] -= mean
        val variance = onset.sumOf { it * it }
        if (variance < 1e-10) return null

        val framesPerSecond = sampleRate.toDouble() / hopSize
        val minLag = (framesPerSecond * 60.0 / 240.0).toInt().coerceAtLeast(1)
        val maxLag = (framesPerSecond * 60.0 / 40.0).toInt()
            .coerceAtMost(onset.size / 2)
        if (maxLag <= minLag) return null

        var bestLag = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            for (i in 0 until onset.size - lag) {
                val a = onset[i]
                val b = onset[i + lag]
                correlation += a * b
                leftEnergy += a * a
                rightEnergy += b * b
            }
            val denominator = sqrt(leftEnergy * rightEnergy)
            val score = if (denominator > 0.0) correlation / denominator else 0.0
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestScore < 0.08) return null
        return (60.0 * framesPerSecond / bestLag).toFloat()
            .takeIf { it in 40f..240f }
    }
}
