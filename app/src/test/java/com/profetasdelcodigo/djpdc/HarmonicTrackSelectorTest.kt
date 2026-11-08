package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonicTrackSelectorTest {
    @Test
    fun sameKeyIsMoreCompatibleThanDistantKey() {
        val cMajor = MusicalKeyProfile(0, MusicalMode.MAJOR, 0.8f)
        val gMajor = MusicalKeyProfile(7, MusicalMode.MAJOR, 0.8f)
        val fSharpMinor = MusicalKeyProfile(6, MusicalMode.MINOR, 0.8f)
        assertTrue(HarmonicTrackSelector.compatibility(cMajor, gMajor) >
            HarmonicTrackSelector.compatibility(cMajor, fSharpMinor))
    }

    @Test
    fun relativeMajorMinorIsHarmonicallyCompatible() {
        val cMajor = MusicalKeyProfile(0, MusicalMode.MAJOR, 0.8f)
        val aMinor = MusicalKeyProfile(9, MusicalMode.MINOR, 0.8f)
        assertTrue(HarmonicTrackSelector.compatibility(cMajor, aMinor) >= 0.25f)
    }

    @Test
    fun selectorPrefersHarmonicallyCompatibleTrack() {
        val current = MusicalKeyProfile(0, MusicalMode.MAJOR, 0.9f)
        val distant = RankedLocalTrack(LocalTrackEntry(1, "Distant", "A", "A", 1), 20, emptySet())
        val compatible = RankedLocalTrack(LocalTrackEntry(2, "Compatible", "A", "A", 1), 10, emptySet())
        val selected = HarmonicTrackSelector.select(
            ranked = listOf(distant, compatible),
            current = current,
            measured = mapOf(
                1L to MusicalKeyProfile(6, MusicalMode.MINOR, 0.9f),
                2L to MusicalKeyProfile(7, MusicalMode.MAJOR, 0.9f)
            )
        )
        assertEquals(2L, selected?.track?.id)
    }

    @Test
    fun selectorFallsBackWhenKeyAnalysisIsMissing() {
        val first = RankedLocalTrack(LocalTrackEntry(1, "A", "A", "A", 1), 5, emptySet())
        val second = RankedLocalTrack(LocalTrackEntry(2, "B", "B", "B", 1), 1, emptySet())
        assertEquals(first, HarmonicTrackSelector.select(listOf(first, second), null, emptyMap()))
    }
}
