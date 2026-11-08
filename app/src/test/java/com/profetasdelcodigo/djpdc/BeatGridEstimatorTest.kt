package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatGridEstimatorTest {
    @Test
    fun estimatesStablePulsePhaseAtKnownTempo() {
        val onsets = (0..12).map { 125L + it * 500L }
        val result = BeatGridEstimator.estimate(onsets, 120f)
        assertNotNull(result)
        assertEquals(500.0, result!!.beatPeriodMs, 0.001)
        assertEquals(125.0, result.phaseOffsetMs, 1.0)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun ignoresNegativeAndDuplicateOnsets() {
        val result = BeatGridEstimator.estimate(
            listOf(-50L, 100L, 100L, 600L, 1_100L, 1_600L, 2_100L, 2_600L),
            120f
        )
        assertNotNull(result)
        assertEquals(6, result!!.supportingOnsets)
    }

    @Test
    fun rejectsInsufficientOrInvalidData() {
        assertNull(BeatGridEstimator.estimate(listOf(0L, 500L, 1_000L), 120f))
        assertNull(BeatGridEstimator.estimate((0..8).map { it * 500L }, null))
        assertNull(BeatGridEstimator.estimate((0..8).map { it * 500L }, Float.NaN))
        assertNull(BeatGridEstimator.estimate((0..8).map { it * 500L }, 300f))
    }

    @Test
    fun rejectsUnstablePulsePhases() {
        val irregular = listOf(0L, 170L, 710L, 1_040L, 1_800L, 2_010L, 2_740L)
        assertNull(BeatGridEstimator.estimate(irregular, 120f, minimumConfidence = 0.8f))
    }
}
