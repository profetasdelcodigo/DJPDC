package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BeatAlignedTransitionPlannerTest {
    private fun grid(bpm: Float, phase: Double, confidence: Float = 0.95f) =
        BeatGridEstimate(bpm, 60_000.0 / bpm, phase, confidence, 12)

    @Test
    fun alignsIncomingCueToNextOutgoingBeat() {
        val result = BeatAlignedTransitionPlanner.plan(
            currentPositionMs = 1_300L,
            currentGrid = grid(120f, 100.0),
            incomingGrid = grid(120f, 250.0)
        )
        assertNotNull(result)
        assertEquals(300L, result!!.delayUntilNextBeatMs)
        assertEquals(250L, result.incomingTrackStartMs)
        assertEquals(1f, result.playbackSpeedRatio, 0.001f)
    }

    @Test
    fun computesBoundedTempoCorrection() {
        val result = BeatAlignedTransitionPlanner.plan(
            currentPositionMs = 0L,
            currentGrid = grid(124f, 80.0),
            incomingGrid = grid(120f, 200.0)
        )
        assertNotNull(result)
        assertEquals(124f / 120f, result!!.playbackSpeedRatio, 0.001f)
    }

    @Test
    fun rejectsUncertainGridsAndExtremeTempoMismatch() {
        assertNull(
            BeatAlignedTransitionPlanner.plan(
                0L, grid(120f, 0.0, 0.4f), grid(120f, 0.0)
            )
        )
        assertNull(
            BeatAlignedTransitionPlanner.plan(
                0L, grid(140f, 0.0), grid(100f, 0.0)
            )
        )
    }

    @Test
    fun rejectsInvalidPositionOrGrid() {
        assertNull(BeatAlignedTransitionPlanner.plan(-1L, grid(120f, 0.0), grid(120f, 0.0)))
        assertNull(BeatAlignedTransitionPlanner.plan(0L, null, grid(120f, 0.0)))
        assertNull(
            BeatAlignedTransitionPlanner.plan(
                0L, grid(120f, 600.0), grid(120f, 0.0)
            )
        )
    }
}
