package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {
    @Test
    fun calculates120BpmFromHalfSecondTaps() {
        assertEquals(120f, TapTempo.bpmFromTimestamps(listOf(0L, 500L, 1000L, 1500L))!!, 0.01f)
    }

    @Test
    fun usesMedianToReduceOneUnevenTap() {
        val bpm = TapTempo.bpmFromTimestamps(listOf(0L, 500L, 1000L, 1800L, 2300L))
        assertEquals(120f, bpm!!, 0.01f)
    }

    @Test
    fun needsAtLeastThreeTaps() {
        assertNull(TapTempo.bpmFromTimestamps(listOf(0L, 500L)))
    }

    @Test
    fun rejectsTempoOutsideSupportedRange() {
        assertNull(TapTempo.bpmFromTimestamps(listOf(0L, 100L, 200L)))
    }
}
