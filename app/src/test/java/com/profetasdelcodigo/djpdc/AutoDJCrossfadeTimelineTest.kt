package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDJCrossfadeTimelineTest {
    @Test
    fun invalidDurationHasNoFrames() {
        assertNull(AutoDJCrossfadeTimeline.frameAt(0L, 0L))
        assertNull(AutoDJCrossfadeTimeline.frameAt(0L, -1L))
    }

    @Test
    fun startAndEndFramesReachExpectedDecks() {
        val start = AutoDJCrossfadeTimeline.frameAt(0L, 6_000L)!!
        val end = AutoDJCrossfadeTimeline.frameAt(6_000L, 6_000L)!!

        assertEquals(1f, start.gains.deckA, 0.0001f)
        assertEquals(0f, start.gains.deckB, 0.0001f)
        assertEquals(0f, end.gains.deckA, 0.0001f)
        assertEquals(1f, end.gains.deckB, 0.0001f)
        assertTrue(end.isComplete)
    }

    @Test
    fun elapsedTimeIsClampedToTimeline() {
        val before = AutoDJCrossfadeTimeline.frameAt(-100L, 5_000L)!!
        val after = AutoDJCrossfadeTimeline.frameAt(9_000L, 5_000L)!!

        assertEquals(0L, before.elapsedMs)
        assertEquals(5_000L, after.elapsedMs)
        assertTrue(after.isComplete)
    }

    @Test
    fun progressAndDeckBGainDoNotMoveBackward() {
        var previousProgress = 0f
        var previousDeckB = 0f
        for (time in 0L..6_000L step 100L) {
            val frame = AutoDJCrossfadeTimeline.frameAt(time, 6_000L)!!
            assertTrue(frame.progress >= previousProgress)
            assertTrue(frame.gains.deckB >= previousDeckB)
            previousProgress = frame.progress
            previousDeckB = frame.gains.deckB
        }
    }

    @Test
    fun manualOverrideOrSessionChangeCancelsTransition() {
        val active = AutoDJSessionState(AutoDJMode.ACTIVE, 10L)
        assertTrue(AutoDJCrossfadeTimeline.mayContinue(active, 10L, 20L, 10L, 20L, false))
        assertFalse(AutoDJCrossfadeTimeline.mayContinue(active, 10L, 20L, 10L, 20L, true))
        assertFalse(AutoDJCrossfadeTimeline.mayContinue(
            active.copy(mode = AutoDJMode.PAUSED), 10L, 20L, 10L, 20L, false
        ))
        assertFalse(AutoDJCrossfadeTimeline.mayContinue(active, 10L, 20L, 11L, 20L, false))
    }
}
