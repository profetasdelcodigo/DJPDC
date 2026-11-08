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
    fun selectorFallsBackWhenKeyAnalysisIsMissing() {
        val first = RankedLocalTrack(LocalTrackEntry(1, "A", "A", "A", 1), 5, emptySet())
        val second = RankedLocalTrack(LocalTrackEntry(2, "B", "B", "B", 1), 1, emptySet())
        assertEquals(first, HarmonicTrackSelector.select(listOf(first, second), null, emptyMap()))
    }
}
