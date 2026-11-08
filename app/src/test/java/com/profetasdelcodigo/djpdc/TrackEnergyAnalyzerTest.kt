package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class TrackEnergyAnalyzerTest {
    @Test
    fun rejectsTooShortAndInvalidAudio() {
        assertNull(TrackEnergyAnalyzer.analyze(FloatArray(100), 44_100))
        assertNull(TrackEnergyAnalyzer.analyze(FloatArray(44_100 * 4) { Float.NaN }, 44_100))
    }

    @Test
    fun returnsBoundedProfileForAudio() {
        val rate = 8_000
        val samples = FloatArray(rate * 8) { index ->
            (0.35 * sin(2.0 * PI * 110.0 * index / rate)).toFloat()
        }
        val profile = TrackEnergyAnalyzer.analyze(samples, rate)
        assertNotNull(profile)
        assertTrue(profile!!.energy in 0f..1f)
        assertTrue(profile.peakEnergy in 0f..1f)
        assertTrue(profile.dynamicRange in 0f..1f)
        assertEquals(8f, profile.analyzedSeconds, 0.01f)
    }

    @Test
    fun selectorPrefersEnergyCompatibleCandidate() {
        val current = TrackEnergyProfile(0.70f, 0.9f, 0.2f, 0.5f, 20f)
        val highMetadataButLowEnergy = RankedLocalTrack(
            LocalTrackEntry(1, "A", "Artist", "Album", 180_000), 100, emptySet()
        )
        val compatible = RankedLocalTrack(
            LocalTrackEntry(2, "B", "Artist", "Album", 180_000), 10, emptySet()
        )
        val result = EnergyAwareTrackSelector.select(
            listOf(highMetadataButLowEnergy, compatible),
            current,
            mapOf(
                1L to TrackEnergyProfile(0.10f, 0.2f, 0.1f, 0.1f, 20f),
                2L to TrackEnergyProfile(0.68f, 0.9f, 0.2f, 0.48f, 20f)
            )
        )
        assertEquals(2L, result?.track?.id)
    }

    @Test
    fun missingProfilesKeepMetadataFallback() {
        val first = RankedLocalTrack(LocalTrackEntry(1, "A", "A", "A", 1), 5, emptySet())
        val second = RankedLocalTrack(LocalTrackEntry(2, "B", "B", "B", 1), 1, emptySet())
        assertEquals(first, EnergyAwareTrackSelector.select(listOf(first, second), null, emptyMap()))
    }
}
