package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeatmatchMathTest {
    @Test
    fun matchesDeckBToDeckAWhenTemposAreClose() {
        assertEquals(1.05f, BeatmatchMath.matchingSpeed(105f, 100f)!!, 0.001f)
    }

    @Test
    fun rejectsCorrectionsOutsideSupportedEightPercentRange() {
        assertNull(BeatmatchMath.matchingSpeed(140f, 100f))
        assertNull(BeatmatchMath.matchingSpeed(80f, 100f))
    }

    @Test
    fun acceptsCorrectionsAtSupportedRangeBoundaries() {
        assertEquals(1.08f, BeatmatchMath.matchingSpeed(108f, 100f)!!, 0.001f)
        assertEquals(0.92f, BeatmatchMath.matchingSpeed(92f, 100f)!!, 0.001f)
    }

    @Test
    fun rejectsMissingOrInvalidTempo() {
        assertNull(BeatmatchMath.matchingSpeed(null, 100f))
        assertNull(BeatmatchMath.matchingSpeed(300f, 100f))
        assertNull(BeatmatchMath.matchingSpeed(Float.NaN, 100f))
    }
}
