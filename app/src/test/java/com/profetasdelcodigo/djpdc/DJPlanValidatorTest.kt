package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DJPlanValidatorTest {
    private val context = DJPlanContext(
        autoDjEnabled = true,
        currentTrackId = 10L,
        accessibleTrackIds = setOf(10L, 20L, 30L),
        nextTrackDurationMs = 240_000L
    )

    private fun plan(
        style: DJTransitionStyle = DJTransitionStyle.BEAT_MATCHED_CROSSFADE,
        confidence: Float = 0.9f,
        startMs: Long = 12_000L,
        speed: Float = 1.04f
    ) = DJTransitionPlan(
        nextTrackId = 20L,
        transitionDurationMs = 8_000L,
        nextTrackStartMs = startMs,
        playbackSpeedRatio = speed,
        beatAlignmentConfidence = confidence,
        style = style
    )

    @Test
    fun acceptsValidBeatMatchedPlan() {
        val result = DJPlanValidator.validate(plan(), context)
        assertTrue(result is DJPlanDecision.Accepted)
        val accepted = result as DJPlanDecision.Accepted
        assertFalse(accepted.usedSafeFallback)
        assertEquals(12_000L, accepted.plan.nextTrackStartMs)
        assertEquals(DJTransitionStyle.BEAT_MATCHED_CROSSFADE, accepted.plan.style)
    }

    @Test
    fun rejectsPlanWhenAutoDjIsOff() {
        val result = DJPlanValidator.validate(
            plan(),
            context.copy(autoDjEnabled = false)
        )
        assertTrue(result is DJPlanDecision.Rejected)
    }

    @Test
    fun rejectsTrackOutsideAccessibleLibrary() {
        val result = DJPlanValidator.validate(
            plan().copy(nextTrackId = 999L),
            context
        )
        assertTrue(result is DJPlanDecision.Rejected)
    }

    @Test
    fun rejectsSameTrackAndMissingCurrentTrack() {
        assertTrue(
            DJPlanValidator.validate(plan().copy(nextTrackId = 10L), context)
                is DJPlanDecision.Rejected
        )
        assertTrue(
            DJPlanValidator.validate(plan(), context.copy(currentTrackId = null))
                is DJPlanDecision.Rejected
        )
    }

    @Test
    fun rejectsOutOfBoundsTransitionAndStartPosition() {
        assertTrue(
            DJPlanValidator.validate(
                plan().copy(transitionDurationMs = 60_000L), context
            ) is DJPlanDecision.Rejected
        )
        assertTrue(
            DJPlanValidator.validate(
                plan(startMs = 240_000L), context
            ) is DJPlanDecision.Rejected
        )
    }

    @Test
    fun rejectsUnsafeSpeedAndInvalidConfidence() {
        assertTrue(
            DJPlanValidator.validate(plan(speed = 1.2f), context)
                is DJPlanDecision.Rejected
        )
        assertTrue(
            DJPlanValidator.validate(plan().copy(beatAlignmentConfidence = Float.NaN), context)
                is DJPlanDecision.Rejected
        )
    }

    @Test
    fun lowBeatConfidenceFallsBackToSimpleFade() {
        val result = DJPlanValidator.validate(plan(confidence = 0.4f), context)
        assertTrue(result is DJPlanDecision.Accepted)
        val accepted = result as DJPlanDecision.Accepted
        assertTrue(accepted.usedSafeFallback)
        assertEquals(DJTransitionStyle.SIMPLE_FADE, accepted.plan.style)
        assertEquals(0L, accepted.plan.nextTrackStartMs)
        assertEquals(1f, accepted.plan.playbackSpeedRatio)
    }

    @Test
    fun rejectsUnknownNextTrackDuration() {
        assertTrue(
            DJPlanValidator.validate(
                plan(),
                context.copy(nextTrackDurationMs = 0L)
            ) is DJPlanDecision.Rejected
        )
    }
}
