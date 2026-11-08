package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BpmAwareTrackSelectorTest {
    private val ranked = listOf(
        RankedLocalTrack(LocalTrackEntry(2, "Candidate A", "Artist", "Album", 180_000L), 10, emptySet()),
        RankedLocalTrack(LocalTrackEntry(3, "Candidate B", "Artist", "Album", 180_000L), 5, emptySet()),
        RankedLocalTrack(LocalTrackEntry(4, "Candidate C", "Artist", "Album", 180_000L), 8, emptySet())
    )

    @Test
    fun choosesClosestCompatibleTempoRatherThanHighestMetadataScore() {
        val result = BpmAwareTrackSelector.select(
            ranked = ranked,
            currentBpm = 120f,
            measuredBpms = mapOf(2L to 110f, 3L to 119f, 4L to 100f)
        )
        assertEquals(3L, result?.track?.track?.id)
        assertEquals(120f / 119f, result?.playbackSpeedRatio ?: 0f, 0.0001f)
    }

    @Test
    fun returnsNullWhenNoCandidateIsWithinSafeTempoRange() {
        val result = BpmAwareTrackSelector.select(
            ranked = ranked,
            currentBpm = 120f,
            measuredBpms = mapOf(2L to 90f, 3L to 80f, 4L to 70f)
        )
        assertNull(result)
    }

    @Test
    fun ignoresCandidatesWithoutMeasuredBpm() {
        val result = BpmAwareTrackSelector.select(
            ranked = ranked,
            currentBpm = 120f,
            measuredBpms = mapOf(2L to 110f)
        )
        assertEquals(2L, result?.track?.track?.id)
    }

    @Test
    fun invalidCurrentTempoDisablesBpmRanking() {
        assertNull(BpmAwareTrackSelector.select(ranked, 0f, mapOf(2L to 120f)))
    }
}
