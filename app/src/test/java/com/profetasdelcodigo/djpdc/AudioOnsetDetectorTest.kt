package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioOnsetDetectorTest {
    private fun pulseAudio(
        sampleRate: Int = 8_000,
        seconds: Int = 8,
        intervalMs: Int = 500
    ): FloatArray {
        val audio = FloatArray(sampleRate * seconds)
        val pulseLength = sampleRate / 30
        val intervalSamples = sampleRate * intervalMs / 1_000
        var start = 0
        while (start < audio.size) {
            for (i in start until (start + pulseLength).coerceAtMost(audio.size)) {
                audio[i] = 0.8f
            }
            start += intervalSamples
        }
        return audio
    }

    @Test
    fun detectsRepeatedTransientEvents() {
        val result = AudioOnsetDetector.detect(pulseAudio(), 8_000)
        assertNotNull(result)
        val times = result!!.onsetTimesMs
        assertTrue("Expected several onsets, got $times", times.size >= 8)
        val intervals = times.zipWithNext { a, b -> b - a }
        assertTrue(intervals.all { abs(it - 500L) <= 100L })
    }

    @Test
    fun silenceReturnsEmptyDetection() {
        val result = AudioOnsetDetector.detect(FloatArray(8_000 * 3), 8_000)
        assertNotNull(result)
        assertTrue(result!!.onsetTimesMs.isEmpty())
        assertEquals(0f, result.confidence)
    }

    @Test
    fun rejectsInvalidInputs() {
        assertNull(AudioOnsetDetector.detect(FloatArray(8_000), 0))
        assertNull(AudioOnsetDetector.detect(FloatArray(8_000), 8_000, windowSize = 64))
        val invalid = pulseAudio()
        invalid[10] = Float.NaN
        assertNull(AudioOnsetDetector.detect(invalid, 8_000))
    }
}
