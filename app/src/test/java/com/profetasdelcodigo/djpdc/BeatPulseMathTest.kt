package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeatPulseMathTest {
    @Test
    fun advancesBeatAndBarAt120Bpm() {
        assertEquals(1, BeatPulseMath.at(0L, 120f)!!.beatInBar)
        assertEquals(2, BeatPulseMath.at(500L, 120f)!!.beatInBar)
        assertEquals(1, BeatPulseMath.at(2_000L, 120f)!!.beatInBar)
    }

    @Test
    fun reportsFractionWithinBeat() {
        assertEquals(0.5f, BeatPulseMath.at(250L, 120f)!!.phase, 0.001f)
    }

    @Test
    fun rejectsInvalidInputs() {
        assertNull(BeatPulseMath.at(-1L, 120f))
        assertNull(BeatPulseMath.at(0L, null))
        assertNull(BeatPulseMath.at(0L, Float.NaN))
        assertNull(BeatPulseMath.at(0L, 20f))
    }
}
