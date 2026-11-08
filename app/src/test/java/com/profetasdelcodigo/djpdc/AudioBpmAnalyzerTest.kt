package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class AudioBpmAnalyzerTest {
    private fun pulseTrack(bpm: Int, seconds: Int = 12, sampleRate: Int = 8_000): FloatArray {
        val samples = FloatArray(seconds * sampleRate)
        val beatSamples = sampleRate * 60 / bpm
        val pulseLength = sampleRate / 20
        var beat = 0
        while (beat * beatSamples < samples.size) {
            val start = beat * beatSamples
            for (i in start until (start + pulseLength).coerceAtMost(samples.size)) {
                samples[i] = 0.9f
            }
            beat++
        }
        return samples
    }

    @Test
    fun estimates120BpmFromSyntheticPulseAudio() {
        val bpm = AudioBpmAnalyzer.estimateBpm(pulseTrack(120), 8_000)
        assertEquals(120f, bpm!!, 3f)
    }

    @Test
    fun estimates100BpmFromSyntheticPulseAudio() {
        val bpm = AudioBpmAnalyzer.estimateBpm(pulseTrack(100), 8_000)
        assertEquals(100f, bpm!!, 3f)
    }

    @Test
    fun rejectsSilenceAndShortAudio() {
        assertNull(AudioBpmAnalyzer.estimateBpm(FloatArray(8_000 * 12), 8_000))
        assertNull(AudioBpmAnalyzer.estimateBpm(FloatArray(8_000 * 3), 8_000))
    }

    @Test
    fun rejectsInvalidSampleRateAndNonFiniteSamples() {
        assertNull(AudioBpmAnalyzer.estimateBpm(FloatArray(8_000 * 10), 0))
        val invalid = pulseTrack(120)
        invalid[100] = Float.NaN
        assertNull(AudioBpmAnalyzer.estimateBpm(invalid, 8_000))
    }
}
